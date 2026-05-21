// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.ai.review.model

import com.intellij.agent.workbench.ai.review.AIReviewBundle
import com.intellij.agent.workbench.ai.review.ui.AIReviewDynamicPanelProvider
import com.intellij.agent.workbench.ai.review.ui.AIReviewProblemsViewPanel
import com.intellij.analysis.problemsView.toolWindow.ProblemsViewToolWindowUtils
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.Disposer
import com.intellij.ui.content.Content
import com.intellij.ui.content.ContentManagerEvent
import com.intellij.ui.content.ContentManagerListener
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Project-level service managing multiple AI review sessions.
 * Each session is represented as a separate tab in the Problems tool window.
 */
@ApiStatus.Internal
@Service(Service.Level.PROJECT)
class AIReviewSessionManager(private val project: Project, val cs: CoroutineScope) {
  private val sessions = ConcurrentHashMap<String, AIReviewSession>()
  private val counter = AtomicInteger(0)
  private val closeListenerInstalled = AtomicBoolean(false)

  fun createSession(
    request: AIReviewRequest.LocalChanges,
    agent: AIReviewAgent? = null,
  ): AIReviewSession {
    val sessionId = "AIReview_${counter.incrementAndGet()}"
    val session = AIReviewSession(project, cs, sessionId, request, agent)
    sessions[sessionId] = session

    val tabName = AIReviewBundle.message("aiReview.problems.tab.name.numbered", counter.get())
    val provider = AIReviewDynamicPanelProvider(project, session, tabName)
    ProblemsViewToolWindowUtils.addTab(project, provider)
    ensureCloseListenerInstalled()
    ProblemsViewToolWindowUtils.selectTab(project, sessionId)

    return session
  }

  fun getSession(sessionId: String): AIReviewSession? = sessions[sessionId]

  fun removeSession(sessionId: String) {
    val session = sessions.remove(sessionId) ?: return
    session.cancelRunningReview()
    ProblemsViewToolWindowUtils.removeTab(project, sessionId)
  }

  fun getActiveSessions(): Collection<AIReviewSession> = sessions.values

  private fun ensureCloseListenerInstalled() {
    if (!closeListenerInstalled.compareAndSet(false, true)) return

    val toolWindow = ProblemsViewToolWindowUtils.getToolWindow(project)
    if (toolWindow == null) {
      closeListenerInstalled.set(false)
      return
    }

    val listener = object : ContentManagerListener {
      override fun contentRemoveQuery(event: ContentManagerEvent) {
        val session = getSession(event.content) ?: return
        val messageKey = if (session.hasRunningReview()) {
          "aiReview.close.confirmation.running.message"
        }
        else {
          "aiReview.close.confirmation.message"
        }

        val result = Messages.showYesNoDialog(
          project,
          AIReviewBundle.message(messageKey),
          AIReviewBundle.message("aiReview.close.confirmation.title"),
          Messages.getQuestionIcon(),
        )
        if (result != Messages.YES) {
          event.consume()
          return
        }

        sessions.remove(session.sessionId)?.cancelRunningReview()
      }

      override fun contentRemoved(event: ContentManagerEvent) {
        sessions.remove(getSessionId(event.content) ?: return)?.cancelRunningReview()
      }
    }

    toolWindow.contentManager.addContentManagerListener(listener)
    Disposer.register(toolWindow.disposable, Disposable {
      toolWindow.contentManagerIfCreated?.removeContentManagerListener(listener)
    })
  }

  private fun getSession(content: Content): AIReviewSession? {
    val sessionId = getSessionId(content) ?: return null
    return sessions[sessionId]
  }

  private fun getSessionId(content: Content): String? {
    return (content.component as? AIReviewProblemsViewPanel)?.session?.sessionId
  }
}
