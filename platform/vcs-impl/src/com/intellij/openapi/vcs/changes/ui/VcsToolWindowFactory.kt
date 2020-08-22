// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.ui

import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.extensions.ExtensionPointListener
import com.intellij.openapi.extensions.PluginDescriptor
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.openapi.vcs.ProjectLevelVcsManager.VCS_CONFIGURATION_CHANGED
import com.intellij.openapi.vcs.VcsListener
import com.intellij.openapi.vcs.changes.ui.ChangesViewContentManager.Companion.CONTENT_PROVIDER_SUPPLIER_KEY
import com.intellij.openapi.vcs.changes.ui.ChangesViewContentManager.Companion.getToolWindowIdFor
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.openapi.wm.ex.ToolWindowEx
import com.intellij.ui.content.Content
import com.intellij.ui.content.ContentFactory
import com.intellij.ui.content.ContentManagerEvent
import com.intellij.ui.content.ContentManagerListener
import com.intellij.util.ui.UIUtil.getClientProperty
import com.intellij.util.ui.UIUtil.putClientProperty
import javax.swing.JPanel

private val Project.changesViewContentManager: ChangesViewContentI
  get() = ChangesViewContentManager.getInstance(this)

private val IS_CONTENT_CREATED = Key.create<Boolean>("ToolWindow.IsContentCreated")
private val CHANGES_VIEW_EXTENSION = Key.create<ChangesViewContentEP>("Content.ChangesViewExtension")

private fun Content.getExtension(): ChangesViewContentEP? = getUserData(CHANGES_VIEW_EXTENSION)

abstract class VcsToolWindowFactory : ToolWindowFactory, DumbAware {
  override fun init(window: ToolWindow) {
    val project = (window as ToolWindowEx).project

    updateState(project, window)
    val connection = project.messageBus.connect()
    connection.subscribe(VCS_CONFIGURATION_CHANGED, VcsListener {
      runInEdt {
        if (project.isDisposed) return@runInEdt
        updateState(project, window)
      }
    })
    connection.subscribe(ChangesViewContentManagerListener.TOPIC, object : ChangesViewContentManagerListener {
      override fun toolWindowMappingChanged() {
        updateState(project, window)
        window.contentManagerIfCreated?.selectFirstContent()
      }
    })
    ChangesViewContentEP.EP_NAME.addExtensionPointListener(project, ExtensionListener(window), window.disposable)

    window.addContentManagerListener(getContentManagerListener(project, window))
  }

  override fun shouldBeAvailable(project: Project): Boolean = project.vcsManager.hasAnyMappings() || project.vcsManager.isConsoleVisible

  override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
    updateContent(project, toolWindow)
    project.changesViewContentManager.attachToolWindow(toolWindow)

    putClientProperty(toolWindow.component, IS_CONTENT_CREATED, true)
  }

  protected open fun updateState(project: Project, toolWindow: ToolWindow) {
    updateAvailability(project, toolWindow)
    updateContentIfCreated(project, toolWindow)
  }

  private fun updateAvailability(project: Project, toolWindow: ToolWindow) {
    val available = shouldBeAvailable(project)

    // force showing stripe button on adding initial mapping even if stripe button was manually removed by the user
    if (available && !toolWindow.isAvailable) {
      toolWindow.isShowStripeButton = true
    }

    toolWindow.isAvailable = available
  }

  private fun updateContentIfCreated(project: Project, toolWindow: ToolWindow) {
    if (getClientProperty(toolWindow.component, IS_CONTENT_CREATED) != true) return
    updateContent(project, toolWindow)
  }

  private fun updateContent(project: Project, toolWindow: ToolWindow) {
    val changesViewContentManager = project.changesViewContentManager

    getExtensions(project, toolWindow).forEach { extension ->
      val isVisible = extension.newPredicateInstance(project)?.`fun`(project) != false
      val content = changesViewContentManager.findContents { it.getExtension() === extension }.firstOrNull()

      if (isVisible && content == null) {
        changesViewContentManager.addContent(createExtensionContent(project, extension))
      }
      else if (!isVisible && content != null) {
        changesViewContentManager.removeContent(content)
      }
    }
  }

  private fun getExtensions(project: Project, toolWindow: ToolWindow): Collection<ChangesViewContentEP> {
    return ChangesViewContentEP.EP_NAME.getExtensions(project).filter { getToolWindowIdFor(project, it.tabName) == toolWindow.id }
  }

  private fun createExtensionContent(project: Project, extension: ChangesViewContentEP): Content {
    val displayName: String = extension.newDisplayNameSupplierInstance(project)?.get() ?: extension.tabName

    return ContentFactory.SERVICE.getInstance().createContent(JPanel(null), displayName, false).apply {
      isCloseable = false
      tabName = extension.tabName
      putUserData(CHANGES_VIEW_EXTENSION, extension)
      putUserData(CONTENT_PROVIDER_SUPPLIER_KEY) { extension.getInstance(project) }

      extension.newPreloaderInstance(project)?.preloadTabContent(this)
    }
  }

  private fun getContentManagerListener(project: Project, toolWindow: ToolWindow) = object : ContentManagerListener {
    override fun contentAdded(event: ContentManagerEvent) = scheduleUpdate()
    override fun contentRemoved(event: ContentManagerEvent) = scheduleUpdate()

    private fun scheduleUpdate() {
      invokeLater {
        if (project.isDisposed) return@invokeLater
        updateState(project, toolWindow)
      }
    }
  }

  private inner class ExtensionListener(private val toolWindow: ToolWindowEx) : ExtensionPointListener<ChangesViewContentEP> {
    override fun extensionAdded(extension: ChangesViewContentEP, pluginDescriptor: PluginDescriptor) =
      updateContentIfCreated(toolWindow.project, toolWindow)

    override fun extensionRemoved(extension: ChangesViewContentEP, pluginDescriptor: PluginDescriptor) {
      val contentManager = toolWindow.contentManagerIfCreated ?: return
      val content = contentManager.contents.firstOrNull { it.getExtension() === extension } ?: return

      toolWindow.project.changesViewContentManager.removeContent(content)
    }
  }

  companion object {
    internal val Project.vcsManager: ProjectLevelVcsManager
      get() = ProjectLevelVcsManager.getInstance(this)
  }
}