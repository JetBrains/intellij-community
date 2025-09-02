// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.impl

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.vcs.VcsNotifier
import com.intellij.openapi.vcs.changes.ui.ChangesViewContentManager
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.ui.content.*
import com.intellij.util.Consumer
import com.intellij.util.ContentUtilEx
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.vcs.log.VcsLogBundle
import com.intellij.vcs.log.VcsLogUi
import com.intellij.vcs.log.ui.*
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.NonNls
import java.util.function.Function
import java.util.function.Supplier
import javax.swing.JComponent

/**
 * Utility methods to operate VCS Log tabs as [Content]s of the [ContentManager] of the VCS toolwindow.
 */
object VcsLogContentUtil {
  @Internal
  const val MAIN_LOG_TAB_NAME: @NonNls String = "Log" // used as tab id, not user-visible

  @Internal
  val DEFAULT_TAB_GROUP_ID: TabGroupId = TabGroupId(MAIN_LOG_TAB_NAME, VcsLogBundle.messagePointer("vcs.log.tab.name"), true)

  private fun getLogUi(c: JComponent): VcsLogUiEx? {
    val uis = VcsLogUiHolder.getLogUis(c)
    require(uis.size <= 1) { "Component $c has more than one log ui: $uis" }
    return uis.singleOrNull()
  }

  private fun componentsSequence(toolWindow: ToolWindow): Sequence<JComponent> {
    val contentManager = toolWindow.contentManagerIfCreated ?: return emptySequence()
    return sequence {
      for (content in contentManager.getContents()) {
        if (content is TabbedContent) {
          content.tabs.forEach { pair ->
            pair.second?.let { yield(it) }
          }
        }
        else {
          yield(content.component)
        }
      }
    }
  }

  internal fun <U : VcsLogUi> findLogUi(toolWindow: ToolWindow, clazz: Class<U>, select: Boolean, condition: (U) -> Boolean): U? {
    componentsSequence(toolWindow).forEach {
      val logUi = getLogUi(it)

      if (logUi != null && clazz.isInstance(logUi)) {
        @Suppress("UNCHECKED_CAST")
        logUi as U
        if (condition(logUi)) {
          if (select) {
            ContentUtilEx.selectContent(toolWindow.contentManager, it, true)
            if (!toolWindow.isVisible) {
              toolWindow.activate(null)
            }
          }
          return logUi
        }
      }
    }
    return null
  }

  @Internal
  fun <U : VcsLogUi> findLogUi(project: Project, clazz: Class<U>, select: Boolean, condition: (U) -> Boolean): U? {
    return getToolWindow(project)?.let { findLogUi(it, clazz, select, condition) }
  }

  @Internal
  @JvmStatic
  fun findSelectedLogUi(toolWindow: ToolWindow): VcsLogUi? {
    val content = toolWindow.contentManagerIfCreated?.selectedContent ?: return null
    return getLogUi(content.component)
  }

  internal fun getId(content: Content): String? {
    return getLogUi(content.component)?.id
  }

  @Internal
  @JvmStatic
  fun <U : VcsLogUiEx> openLogTab(
    project: Project,
    logManager: VcsLogManager,
    tabGroupId: TabGroupId,
    ui: U,
    tabDisplayName: Function<U, @NlsContexts.TabTitle String>,
    focus: Boolean,
  ) {
    val toolWindow = getToolWindowOrThrow(project)
    openLogTab(logManager, toolWindow, tabGroupId, ui, tabDisplayName, focus)
  }

  internal fun <U : VcsLogUiEx> openLogTab(
    logManager: VcsLogManager,
    toolWindow: ToolWindow,
    tabGroupId: TabGroupId,
    logUi: U,
    tabDisplayName: Function<U, @NlsContexts.TabTitle String>,
    focus: Boolean,
  ) {
    val component = VcsLogPanel(logManager, logUi)
    @Suppress("HardCodedStringLiteral")
    val tabDescriptor = TabDescriptor(component, Supplier { tabDisplayName.apply(logUi) }, logUi)
    val contentManager = toolWindow.contentManager
    ContentUtilEx.addTabbedContent(contentManager, tabGroupId, tabDescriptor, focus)
    Disposer.register(logUi) {
      ContentUtilEx.closeContentTab(contentManager) { it === component }
    }
    if (focus) {
      toolWindow.activate(null)
    }
  }

  @Deprecated("Use {@link VcsProjectLog#runInMainLog} instead",
              ReplaceWith("VcsProjectLog.runInMainLog(project, consumer)", "com.intellij.vcs.log.impl.VcsProjectLog"))
  @RequiresEdt
  @JvmStatic
  fun runInMainLog(project: Project, consumer: Consumer<in MainVcsLogUi>) {
    VcsProjectLog.runInMainLog(project) {
      consumer.consume(it)
    }
  }

  @Internal
  fun showLogIsNotAvailableMessage(project: Project) {
    VcsNotifier.getInstance(project).notifyWarning(VcsLogNotificationIdsHolder.LOG_NOT_AVAILABLE, "",
                                                   VcsLogBundle.message("vcs.log.is.not.available"))
  }

  internal fun findMainLog(cm: ContentManager): Content? {
    // here tab name is used instead of log ui id to select the correct tab
    // it's done this way since main log ui may not be created when this method is called
    return cm.contents.find { MAIN_LOG_TAB_NAME == it.tabName }
  }

  internal fun selectMainLog(toolWindow: ToolWindow): Boolean {
    val cm = toolWindow.contentManager
    val mainContent = findMainLog(cm) ?: return false
    cm.setSelectedContent(mainContent)
    return true
  }

  @Internal
  fun selectMainLog(project: Project): Boolean {
    val toolWindow = getToolWindow(project) ?: return false
    return selectMainLog(toolWindow)
  }

  @Internal
  @JvmStatic
  fun updateLogUiName(project: Project, ui: VcsLogUi) {
    val toolWindow = getToolWindow(project) ?: return
    val manager = toolWindow.contentManager
    val component = ContentUtilEx.findContentComponent(manager) { c: JComponent -> ui === getLogUi(c) } ?: return
    ContentUtilEx.updateTabbedContentDisplayName(manager, component)
  }

  internal fun getToolWindowOrThrow(project: Project): ToolWindow {
    val toolWindow = getToolWindow(project)
    if (toolWindow != null) return toolWindow
    throw IllegalStateException("Could not find tool window for id ${ChangesViewContentManager.TOOLWINDOW_ID}")
  }

  internal fun getToolWindow(project: Project): ToolWindow? {
    return ToolWindowManager.getInstance(project).getToolWindow(ChangesViewContentManager.TOOLWINDOW_ID)
  }
}