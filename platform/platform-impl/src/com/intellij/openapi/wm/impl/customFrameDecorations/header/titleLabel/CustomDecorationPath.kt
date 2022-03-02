// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.customFrameDecorations.header.titleLabel

import com.intellij.ide.RecentProjectsManager
import com.intellij.ide.RecentProjectsManagerBase
import com.intellij.ide.lightEdit.LightEdit
import com.intellij.ide.ui.UISettings
import com.intellij.ide.ui.UISettingsListener
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.project.ProjectManagerListener
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.registry.RegistryValue
import com.intellij.openapi.util.registry.RegistryValueListener
import com.intellij.openapi.wm.impl.customFrameDecorations.header.title.CustomHeaderTitle
import com.intellij.ui.awt.RelativeRectangle
import com.intellij.util.ui.JBUI.CurrentTheme.CustomFrameDecorations
import java.awt.Rectangle
import java.beans.PropertyChangeListener
import javax.swing.JComponent
import javax.swing.JFrame

internal open class CustomDecorationPath(val frame: JFrame) : SelectedEditorFilePath(), CustomHeaderTitle {
  companion object {
    fun createInstance(frame: JFrame): CustomDecorationPath {
      return CustomDecorationPath(frame)
    }

    fun createMainInstance(frame: JFrame): CustomDecorationPath {
      return MainCustomDecorationPath(frame)
    }
  }

  private val projectManagerListener = object : ProjectManagerListener {
    override fun projectOpened(project: Project) {
      checkOpenedProjects()
    }

    override fun projectClosed(project: Project) {
      checkOpenedProjects()
    }
  }

  private fun checkOpenedProjects() {
    val currentProject = project ?: return
    val manager = RecentProjectsManager.getInstance() as RecentProjectsManagerBase
    val currentPath = manager.getProjectPath(currentProject) ?: return
    val currentName = manager.getProjectName(currentPath)
    val sameNameInRecent = manager.getRecentPaths().any {
      currentPath != it && currentName == manager.getProjectName(it)
    }
    val sameNameInOpen = ProjectManager.getInstance().openProjects.any {
      val path = manager.getProjectPath(it) ?: return@any false
      val name = manager.getProjectName(path)
      currentPath != path && currentName == name
    }
    multipleSameNamed = sameNameInRecent || sameNameInOpen
  }

  private val titleChangeListener = PropertyChangeListener {
    updateProjectPath()
  }

  override fun getCustomTitle(): String? {
    if (LightEdit.owns(project)) {
      return frame.title
    }
    return null
  }

  override fun setActive(value: Boolean) {
    val color = if (value) CustomFrameDecorations.titlePaneInfoForeground() else CustomFrameDecorations.titlePaneInactiveInfoForeground()

    view.foreground = color
  }

  override fun getBoundList(): List<RelativeRectangle> {
    return if (!toolTipNeeded) {
      emptyList()
    }
    else {
      val hitTestSpots = ArrayList<RelativeRectangle>()
      hitTestSpots.addAll(getMouseInsetList(label))
      hitTestSpots
    }
  }

  override fun installListeners() {
    super.installListeners()
    frame.addPropertyChangeListener("title", titleChangeListener)
  }

  override fun addAdditionalListeners(disp: Disposable) {
    super.addAdditionalListeners(disp)

    project?.let {
      val busConnection = ApplicationManager.getApplication().messageBus.connect(disp)
      busConnection.subscribe(ProjectManager.TOPIC, projectManagerListener)
      busConnection.subscribe(UISettingsListener.TOPIC, UISettingsListener { checkTabPlacement() })

      checkTabPlacement()
      checkOpenedProjects()
    }
  }

  private fun checkTabPlacement() {
    classPathNeeded = UISettings.getInstance().editorTabPlacement == 0
  }

  override fun unInstallListeners() {
    super.unInstallListeners()
    frame.removePropertyChangeListener(titleChangeListener)
  }

  private fun getMouseInsetList(view: JComponent,
                                mouseInsets: Int = 1): List<RelativeRectangle> {
    return listOf(
      RelativeRectangle(view, Rectangle(0, 0, mouseInsets, view.height)),
      RelativeRectangle(view, Rectangle(0, 0, view.width, mouseInsets)),
      RelativeRectangle(view,
        Rectangle(0, view.height - mouseInsets, view.width, mouseInsets)),
      RelativeRectangle(view,
        Rectangle(view.width - mouseInsets, 0, mouseInsets, view.height))
    )
  }
}

internal class MainCustomDecorationPath(frame: JFrame) : CustomDecorationPath(frame) {
  private val classKey = "ide.borderless.tab.caption.in.title"

  private val registryListener = object : RegistryValueListener {
    override fun afterValueChanged(value: RegistryValue) {
      updatePaths()
    }
  }

  override val captionInTitle: Boolean
    get() = Registry.get(classKey).asBoolean()

  override fun addAdditionalListeners(disp: Disposable) {
    super.addAdditionalListeners(disp)
    Registry.get(classKey).addListener(registryListener, disp)
  }
}