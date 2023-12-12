package com.intellij.ae.database.counters.community.events

import com.intellij.ae.database.activities.WritableDatabaseBackedCounterUserActivity
import com.intellij.ae.database.runUpdateEvent
import com.intellij.ml.llm.core.chat.messages.ChatMessageAuthor
import com.intellij.ml.llm.core.chat.messages.CompletableMessage
import com.intellij.ml.llm.core.chat.messages.CompletedState
import com.intellij.ml.llm.core.chat.session.ChatSession
import com.intellij.ml.llm.core.chat.session.ChatSessionHost
import com.intellij.ml.llm.core.chat.session.ChatSessionHostListener
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.util.Disposer
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

object AIAssistantMessagesCountCounterUserActivity : WritableDatabaseBackedCounterUserActivity() {
  override val id: String
    get() = "ai.assistant.messages.count"

  internal suspend fun submitMessage(markdownMessage: String) {
    val lines = countLinesOfCode(markdownMessage)
    if (lines > 0) {
      submit(lines)
    }
  }

  private fun countLinesOfCode(markdown: String): Int {
    var isInsideCodeBlock = false
    var lineCount = 0

    val lines = markdown.lines()
    for (line in lines) {
      if (line.contains("```")) { // we're not developing markdown parser, so this would work here
        isInsideCodeBlock = !isInsideCodeBlock
      }
      if (isInsideCodeBlock) {
        ++lineCount
      }
    }

    return lineCount
  }
}


internal class AIAssistantMessagesSentUserActivityListenerAttach : ProjectActivity {
  override suspend fun execute(project: Project) {
    val listener = AIAssistantMessagesSentUserActivityChatSessionHostListener()

    Disposer.register(FeatureUsageDatabaseCountersScopeProvider.getDisposable(), Disposable {
      ChatSessionHost.getInstance(project).removeListener(listener)
    })
    ChatSessionHost.getInstance(project).addListener(listener)
  }
}

internal class AIAssistantMessagesSentUserActivityChatSessionHostListener : ChatSessionHostListener {
  override fun chatCreated(session: ChatSession) {
    FeatureUsageDatabaseCountersScopeProvider.getScope().launch {
      session.messagesFlow.collect {
        val messages = it.filterIsInstance<CompletableMessage>().filter { it.author == ChatMessageAuthor.Assistant }
        for (message in messages) {
          launch {
            // await until message is completed
            message.stateFlow.firstOrNull { it is CompletedState }

            FeatureUsageDatabaseCountersScopeProvider.getScope().runUpdateEvent(AIAssistantMessagesCountCounterUserActivity) {
              it.submitMessage(message.text)
            }
          }
        }
      }
    }
  }

  override fun chatRemoved(session: ChatSession) {}
}