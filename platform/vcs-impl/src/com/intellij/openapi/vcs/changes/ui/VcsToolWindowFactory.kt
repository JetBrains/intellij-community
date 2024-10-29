// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes.ui

import com.intellij.ide.actions.ToolWindowEmptyStateAction
import com.intellij.openapi.actionSystem.EdtNoGetDataProvider
import com.intellij.openapi.application.AppUIExecutor
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.extensions.ExtensionPointListener
import com.intellij.openapi.extensions.PluginDescriptor
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.openapi.vcs.ProjectLevelVcsManager.VCS_CONFIGURATION_CHANGED
import com.intellij.openapi.vcs.VcsListener
import com.intellij.openapi.vcs.changes.ui.ChangesViewContentManager.Companion.CONTENT_PROVIDER_SUPPLIER_KEY
import com.intellij.openapi.vcs.changes.ui.ChangesViewContentManager.Companion.IS_IN_COMMIT_TOOLWINDOW_KEY
import com.intellij.openapi.vcs.ex.ProjectLevelVcsManagerEx
import com.intellij.openapi.vcs.ex.VcsActivationListener
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.openapi.wm.ex.ToolWindowEx
import com.intellij.ui.ClientProperty
import com.intellij.ui.content.Content
import com.intellij.ui.content.ContentFactory
import com.intellij.util.ui.StatusText
import javax.swing.JPanel

private val IS_CONTENT_CREATED = Key.create<Boolean>("ToolWindow.IsContentCreated")
internal val CHANGES_VIEW_EXTENSION = Key.create<ChangesViewContentEP>("Content.ChangesViewExtension")

abstract class VcsToolWindowFactory : ToolWindowFactory, DumbAware {
  override fun init(window: ToolWindow) {
    val connection = window.project.messageBus.connect(window.disposable)
    connection.subscribe(VCS_CONFIGURATION_CHANGED, VcsListener {
      AppUIExecutor.onUiThread().expireWith(window.disposable).execute {
        updateState(window)
      }
    })
    connection.subscribe(ProjectLevelVcsManagerEx.VCS_ACTIVATED, VcsActivationListener {
      AppUIExecutor.onUiThread().expireWith(window.disposable).execute {
        updateState(window)
      }
    })
    connection.subscribe(ChangesViewContentManagerListener.TOPIC, object : ChangesViewContentManagerListener {
      override fun toolWindowMappingChanged() {
        updateState(window)
      }
    })
    ChangesViewContentEP.EP_NAME.addExtensionPointListener(window.project, ExtensionListener(window), window.disposable)

    val vcsManager = ProjectLevelVcsManager.getInstance(window.project)
    if (vcsManager != null && vcsManager.areVcsesActivated()) {
      // already is activated - we missed the event, so, call explicitly
      // must be executed later, because we set toolWindow.isAvailable (cannot be called in the init directly)
      ApplicationManager.getApplication().invokeLater({ updateState(window) }, window.project.disposed)
    }
  }

  override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
    updateContent(toolWindow)
    ChangesViewContentManager.getInstance(project).attachToolWindow(toolWindow)
    toolWindow.component.putClientProperty(IS_CONTENT_CREATED, true)

    val contentManager = toolWindow.contentManager
    contentManager.addDataProvider(EdtNoGetDataProvider { sink ->
      sink[ChangesViewContentManager.CONTENT_TAB_NAME_KEY] = contentManager.selectedContent?.tabName
    })
  }

  protected open fun updateState(toolWindow: ToolWindow) {
    toolWindow.isAvailable = isAvailable(toolWindow.project)
    updateContentIfCreated(toolWindow)
    updateEmptyState(toolWindow)
  }

  // shouldBeAvailable cannot be used -
  // for example, ProjectLevelVcsManager.getInstance(project).hasAnyMappings() maybe called only after project is loaded
  // (updated later on event)
  abstract fun isAvailable(project: Project): Boolean

  // final override to make sure that it is not overridden by mistake
  final override fun shouldBeAvailable(project: Project) = false

  private fun updateEmptyState(toolWindow: ToolWindow) {
    val emptyText = (toolWindow as? ToolWindowEx)?.emptyText ?: return

    ToolWindowEmptyStateAction.setEmptyStateBackground(toolWindow)
    emptyText.clear()

    // do not show empty state while project is being opened (as it might already have configured VCS)
    val vcsManager = ProjectLevelVcsManagerEx.getInstanceEx(toolWindow.project)
    if (vcsManager.areVcsesActivated() && !vcsManager.hasActiveVcss()) {
      setEmptyState(toolWindow.project, emptyText)
    }
  }

  protected open fun setEmptyState(project: Project, state: StatusText) {
  }
}

private fun getExtension(content: Content): ChangesViewContentEP? = content.getUserData(CHANGES_VIEW_EXTENSION)

private class ExtensionListener(private val toolWindow: ToolWindow) : ExtensionPointListener<ChangesViewContentEP> {
  override fun extensionAdded(extension: ChangesViewContentEP, pluginDescriptor: PluginDescriptor) {
    updateContentIfCreated(toolWindow)
  }

  override fun extensionRemoved(extension: ChangesViewContentEP, pluginDescriptor: PluginDescriptor) {
    val contentManager = toolWindow.contentManagerIfCreated ?: return
    val content = contentManager.contents.firstOrNull { getExtension(it) === extension } ?: return
    ChangesViewContentManager.getInstance(toolWindow.project).removeContent(content)
  }
}

private fun updateContentIfCreated(toolWindow: ToolWindow) {
  if (ClientProperty.isTrue(toolWindow.component, IS_CONTENT_CREATED)) {
    updateContent(toolWindow)
  }
}

private fun updateContent(toolWindow: ToolWindow) {
  val changesViewContentManager = ChangesViewContentManager.getInstance(toolWindow.project)

  val wasEmpty = toolWindow.contentManager.contentCount == 0
  getExtensions(toolWindow).forEach { extension ->
    val project = toolWindow.project
    val isVisible = extension.newPredicateInstance(project)?.test(project) != false
    val content = changesViewContentManager.findContents { getExtension(it) === extension }.firstOrNull()
    if (isVisible && content == null) {
      changesViewContentManager.addContent(createExtensionContent(project, extension))
    }
    else if (!isVisible && content != null) {
      changesViewContentManager.removeContent(content)
    }
  }
  if (wasEmpty) {
    toolWindow.contentManager.selectFirstContent()
  }
}

private fun getExtensions(toolWindow: ToolWindow): Sequence<ChangesViewContentEP> {
  return ChangesViewContentEP.EP_NAME.getExtensions(toolWindow.project)
    .asSequence()
    .filter {
      toolWindow.id == ChangesViewContentManager.getToolWindowId(toolWindow.project, it)
    }
}

private fun createExtensionContent(project: Project, extension: ChangesViewContentEP): Content {
  val displayName = extension.getDisplayName(project) ?: extension.tabName

  return ContentFactory.getInstance().createContent(JPanel(null), displayName, false).apply {
    isCloseable = false
    tabName = extension.tabName //NON-NLS overridden by displayName above
    putUserData(CHANGES_VIEW_EXTENSION, extension)
    putUserData(CONTENT_PROVIDER_SUPPLIER_KEY) { extension.getInstance(project) }
    putUserData(IS_IN_COMMIT_TOOLWINDOW_KEY, extension.isInCommitToolWindow)

    extension.newPreloaderInstance(project)?.preloadTabContent(this)
  }
}