// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.whatsNew

import com.intellij.ide.DataManager
import com.intellij.ide.IdeBundle
import com.intellij.ide.actions.WhatsNewAction
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.runAndLogException
import com.intellij.openapi.project.Project
import com.intellij.platform.whatsNew.reaction.FUSReactionChecker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import org.jetbrains.concurrency.await

internal class WhatsNewAction : AnAction(), com.intellij.openapi.project.DumbAware {
  companion object {
    private val LOG = logger<WhatsNewAction>()
    internal const val PLACE = "WhatsNew"
    private const val REACTIONS_STATE = "whatsnew.reactions.state"
    private val reactionChecker = FUSReactionChecker(REACTIONS_STATE)

    fun refresh() {
      reactionChecker.clearLikenessState()
      if (LOG.isTraceEnabled) {
        LOG.trace("EapWhatsNew reaction refresh")
      }
    }
  }

  private val contentAsync by lazy {
    appScope.async {
      WhatsNewContent.getWhatsNewContent()
    }
  }

  suspend fun openWhatsNew(project: Project) {
    LOG.info("Open What's New page requested.")
    val dataContext = LOG.runAndLogException { DataManager.getInstance().dataContextFromFocusAsync.await() }
    openWhatsNewPage(project, dataContext)
  }

  private suspend fun openWhatsNewPage(project: Project, dataContext: DataContext?, triggeredByUser: Boolean = false) {
    val content = contentAsync.await()
    if (content != null && content.isAvailable()) {
      content.show(project, dataContext, triggeredByUser, reactionChecker)
    }
    else {
      LOG.warn("EapWhatsNew: can't be shown. Content not available")
    }
  }

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.BGT
  }

  @OptIn(ExperimentalCoroutinesApi::class)
  override fun update(e: AnActionEvent) {
      e.presentation.isEnabledAndVisible = if (contentAsync.isCompleted) contentAsync.getCompleted() != null else false
      e.presentation.setText(IdeBundle.messagePointer("whats.new.action.custom.text", ApplicationNamesInfo.getInstance().fullProductName))
      e.presentation.setDescription(IdeBundle.messagePointer("whats.new.action.custom.description", ApplicationNamesInfo.getInstance().fullProductName))
  }

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project
    if (project != null) {
      project.getScope().launch {
        openWhatsNewPage(project, e.dataContext, true)
      }
    } else {
      LOG.warn("Cannot open what's new action page because project is null")
    }
  }
}

@Service(Service.Level.PROJECT)
private class ScopeProvider(val scope: CoroutineScope)
private fun Project.getScope() = this.service<ScopeProvider>().scope

@Service(Service.Level.APP)
private class AppScopeProvider(val scope: CoroutineScope)
private val appScope: CoroutineScope
  get() = service<AppScopeProvider>().scope
