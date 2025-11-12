/*---------------------------------------------------------------------------------------------
 *  Copyright (c) 2024 Huawei Technologies Co., Ltd. All rights reserved.
 *  This file is a part of the ModelEngine Project.
 *  Licensed under the MIT License. See License.txt in the project root for license information.
 *--------------------------------------------------------------------------------------------*/

package modelengine.fel.community.model.openai.entity.chat;

import modelengine.fel.core.chat.ChatMessage;
import modelengine.fel.core.chat.support.AiMessage;
import modelengine.fel.core.tool.ToolCall;
import modelengine.fitframework.annotation.Alias;
import modelengine.fitframework.annotation.Aliases;
import modelengine.fitframework.util.CollectionUtils;
import modelengine.fitframework.util.StringUtils;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/**
 * OpenAi API 格式的会话补全响应。
 *
 * @author 易文渊
 * @author 张庭怿
 * @since 2024-4-30
 */
public class OpenAiChatCompletionResponse {
    private static final ChatMessage EMPTY_RESPONSE = new AiMessage(StringUtils.EMPTY);

    private List<OpenAiChatCompletionChoice> choices;

    /**
     * 获取响应中的消息。
     *
     * @return 表示模型回复的 {@link ChatMessage}。
     */
    public ChatMessage message() {
        return extractMessage(OpenAiChatMessage::content, OpenAiChatMessage::toolCalls);
    }

    /**
     * 获取响应中的模型推理。
     *
     * @return 表示模型回复的 {@link ChatMessage}。
     */
    public ChatMessage reasoningContent() {
        return extractMessage(OpenAiChatMessage::reasoningContent, OpenAiChatMessage::toolCalls);
    }

    private ChatMessage extractMessage(
            Function<OpenAiChatMessage, Object> contentExtractor,
            Function<OpenAiChatMessage, List<ToolCall>> toolCallsExtractor) {
        if (CollectionUtils.isEmpty(choices)) {
            return EMPTY_RESPONSE;
        }
        // 优先使用 delta (流式响应), 如果 delta 为空则使用 message (兼容 ModelScope)
        OpenAiChatMessage openAiChatMessage = choices.get(0).getEffectiveMessage();
        if (openAiChatMessage == null) {
            return EMPTY_RESPONSE;
        }

        String content = Optional.ofNullable(contentExtractor.apply(openAiChatMessage))
                .filter(obj -> obj instanceof String)
                .map(obj -> (String) obj)
                .orElse(StringUtils.EMPTY);

        List<ToolCall> toolCalls = Optional.ofNullable(toolCallsExtractor.apply(openAiChatMessage))
                .orElse(Collections.emptyList());

        return new AiMessage(content, toolCalls);
    }

    /**
     * 模型响应消息。
     */
    public static class OpenAiChatCompletionChoice {
        // 流式响应使用 delta 字段
        private OpenAiChatMessage delta;
        // 非流式响应使用 message 字段 (某些 API 如 ModelScope 会同时返回两者)
        private OpenAiChatMessage message;

        /**
         * 获取有效的消息对象。
         * 优先返回 delta (流式响应), 如果 delta 不存在或内容为空则返回 message。
         * 这样可以兼容同时返回 delta 和 message 的 API (如 ModelScope)。
         *
         * @return 表示有效消息的 {@link OpenAiChatMessage}。
         */
        public OpenAiChatMessage getEffectiveMessage() {
            // 如果 delta 存在且有内容，优先使用 delta
            if (delta != null && hasContent(delta)) {
                return delta;
            }
            // 否则使用 message
            return message;
        }

        /**
         * 检查消息对象是否包含有效内容。
         *
         * @param msg 表示待检查的消息对象。
         * @return 如果包含内容则返回 true。
         */
        private boolean hasContent(OpenAiChatMessage msg) {
            Object content = msg.content();
            if (content instanceof String) {
                return StringUtils.isNotEmpty((String) content);
            }
            // 对于 reasoning_content 或 tool_calls 的情况
            return StringUtils.isNotEmpty(msg.reasoningContent())
                    || CollectionUtils.isNotEmpty(msg.toolCalls());
        }
    }
}
