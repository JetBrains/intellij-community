// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.light

import com.intellij.ide.lightEdit.LightEdit
import com.intellij.ide.lightEdit.LightEditCompatible
import com.intellij.ide.lightEdit.LightEditService
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.text.HtmlBuilder
import com.intellij.openapi.wm.StatusBar
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.openapi.wm.StatusBarWidgetFactory
import com.intellij.util.Consumer
import git4idea.i18n.GitBundle
import java.awt.Component
import java.awt.event.MouseEvent

private const val ID = "light.edit.git"
private val LOG = Logger.getInstance("#git4idea.light.LightGitStatusBarWidget")

private class LightGitStatusBarWidget(private val lightGitTracker: LightGitTracker) : StatusBarWidget, StatusBarWidget.TextPresentation {
  private var statusBar: StatusBar? = null

  init {
    lightGitTracker.addUpdateListener(object : LightGitTrackerListener {
      override fun update() {
        statusBar?.updateWidget(ID())
      }
    }, this)
  }

  override fun ID(): String = ID

  override fun install(statusBar: StatusBar) {
    this.statusBar = statusBar
  }

  override fun getPresentation(): StatusBarWidget.WidgetPresentation = this

  override fun getText(): String {
    return lightGitTracker.currentLocation?.let { GitBundle.message("git.light.status.bar.text", it) } ?: ""
  }

  override fun getTooltipText(): String {
    val locationText = lightGitTracker.currentLocation?.let { GitBundle.message("git.light.status.bar.tooltip", it) } ?: ""
    if (locationText.isBlank()) return locationText

    val selectedFile = LightEditService.getInstance().selectedFile
    if (selectedFile != null) {
      val statusText = lightGitTracker.getFileStatus(selectedFile).getPresentation()
      if (statusText.isNotBlank()) return HtmlBuilder().append(locationText).br().append(statusText).toString()
    }
    return locationText
  }

  override fun getAlignment(): Float = Component.LEFT_ALIGNMENT

  override fun getClickConsumer(): Consumer<MouseEvent>? = null

  override fun dispose() = Unit
}

class LightGitStatusBarWidgetFactory: StatusBarWidgetFactory, LightEditCompatible {
  override fun getId(): String = ID

  override fun getDisplayName(): String = GitBundle.message("git.light.status.bar.display.name")

  override fun isAvailable(project: Project): Boolean = LightEdit.owns(project)

  override fun createWidget(project: Project): StatusBarWidget {
    LOG.assertTrue(LightEdit.owns(project))
    return LightGitStatusBarWidget(LightGitTracker.getInstance())
  }

  override fun disposeWidget(widget: StatusBarWidget) = Disposer.dispose(widget)

  override fun canBeEnabledOn(statusBar: StatusBar): Boolean = true
}