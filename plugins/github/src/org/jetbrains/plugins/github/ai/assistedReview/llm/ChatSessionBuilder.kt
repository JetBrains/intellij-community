// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.ai.assistedReview.llm

import com.intellij.ml.llm.core.chat.context.ChatContextItem
import com.intellij.ml.llm.core.chat.messages.ChatMessageAuthor
import com.intellij.ml.llm.core.chat.messages.CompletableMessage
import com.intellij.ml.llm.core.chat.messages.impl.UserMessage
import com.intellij.ml.llm.core.chat.session.*
import com.intellij.ml.llm.privacy.trustedStringBuilders.privacyUnsafeDoNotUse
import com.intellij.openapi.project.Project
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

suspend fun chatSession(project: Project, init: ChatSessionBuilder.() -> Unit): ChatSession {
  val builder = ChatSessionBuilder(project)
  init(builder)
  return builder.build()
}

class ChatSessionBuilder(private val project: Project) {
  private val initialChatMessages = mutableListOf<ChatContextItem>()

  fun user(text: String) {
    initialChatMessages.add(ChatContextItem(ChatMessageAuthor.User.toString(), text.privacyUnsafeDoNotUse))
  }

  fun assistant(text: String) {
    initialChatMessages.add(ChatContextItem(ChatMessageAuthor.Assistant.toString(), text.privacyUnsafeDoNotUse))
  }

  suspend fun build(): ChatSession {
    val chatCreationContext = ChatCreationContext(
      ChatOrigin.AIAssistantTool,
      ChatSessionStorage.SourceAction.NEW_CHAT,
      extraItems = initialChatMessages
    )
    return ChatSessionHost.getInstance(project).createChatSession(chatCreationContext)
  }
}

suspend fun ChatSession.ask(project: Project, displayPrompt: String, prompt: String): String =
  askRaw(project, displayPrompt, prompt).waitForCompletion()

suspend fun ChatSession.askRaw(project: Project, displayPrompt: String, prompt: String): CompletableMessage {
  var resultMessage: CompletableMessage? = null
  sendMessage(
    project = project,
    kind = SimpleChat,
    userMessage = UserMessage(
      chat = this,
      formattedDisplayText = displayPrompt.toFormattedString(),
      formattedText = prompt.toFormattedString()
    )
  )
  {
    launch(Dispatchers.Default) {
      it.textFlow.collect {
        println(it)
      }
    }
    resultMessage = it
  }
  return resultMessage ?: error("Failed to receive CompletableMessage")
}