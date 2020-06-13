// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm.impl.customFrameDecorations.header.titleLabel

import com.intellij.ide.RecentProjectsManager
import com.intellij.ide.RecentProjectsManagerBase
import com.intellij.ide.ui.UISettings
import com.intellij.ide.ui.UISettingsListener
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.project.ProjectManagerListener
import com.intellij.openapi.util.Disposer
import com.intellij.ui.awt.RelativeRectangle
import com.intellij.util.ui.JBUI.CurrentTheme.CustomFrameDecorations
import java.awt.Rectangle
import java.beans.PropertyChangeListener
import java.util.*
import javax.swing.JComponent
import javax.swing.JFrame

class CustomDecorationPath(val frame: JFrame, onBoundsChanged: () -> Unit) : SelectedEditorFilePath(onBoundsChanged) {
  private val projectManagerListener = object : ProjectManagerListener {
    override fun projectOpened(project: Project) {
      checkOpenedProjects()
    }

    override fun projectClosed(project: Project) {
      checkOpenedProjects()
    }
  }

  private fun checkOpenedProjects() {
    val instance = ProjectManager.getInstance()
    project?.let { pr ->
      val recentProjectManager = RecentProjectsManager.getInstance() as RecentProjectsManagerBase
      val recentSame = LinkedHashSet(recentProjectManager.getRecentPaths()).any { recentProjectManager.getProjectName(it) == pr.name && pr.basePath != it}
      multipleSameNamed = instance.openProjects.any { it != pr && it.name == pr.name } || recentSame
    }
  }

  private val titleChangeListener = PropertyChangeListener{
    updateProject()
  }

  fun setActive(value: Boolean) {
    val color = if (value) CustomFrameDecorations.titlePaneInfoForeground() else CustomFrameDecorations.titlePaneInactiveInfoForeground()

    getView().foreground = color
  }

  fun getListenerBounds(): List<RelativeRectangle> {
    return if (!toolTipNeeded) {
      emptyList()
    }
    else {
      val hitTestSpots = ArrayList<RelativeRectangle>()
      hitTestSpots.addAll(getMouseInsetList(label))
      hitTestSpots
    }
  }

  var disposable: Disposable? = null

  override fun installListeners() {
    super.installListeners()
    frame.addPropertyChangeListener("title", titleChangeListener)

    disposable?.let {
      if(!Disposer.isDisposed(it)) it.dispose()
    }

    project?.let {
      val ds = Disposer.newDisposable()
      Disposer.register(it, ds)

      val busConnection = ApplicationManager.getApplication().messageBus.connect(ds)
      busConnection.subscribe(ProjectManager.TOPIC, projectManagerListener)
      busConnection.subscribe(UISettingsListener.TOPIC, UISettingsListener { checkTabPlacement() })

      disposable = ds
      checkTabPlacement()
      checkOpenedProjects()
    }
  }

  private fun checkTabPlacement() {
    classPathNeeded = UISettings.instance.editorTabPlacement == 0
  }

  override fun unInstallListeners() {
    super.unInstallListeners()
    disposable?.let {
      if(!Disposer.isDisposed(it)) it.dispose()
    }
    disposable = null
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