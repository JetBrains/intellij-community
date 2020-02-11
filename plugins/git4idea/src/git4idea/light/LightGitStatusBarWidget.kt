// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.light

import com.intellij.ide.lightEdit.LightEditService
import com.intellij.ide.lightEdit.LightEditUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.*
import com.intellij.util.Consumer
import git4idea.index.getPresentation
import java.awt.Component
import java.awt.event.MouseEvent

private class LightGitStatusBarWidget(private val lightGitTracker: LightGitTracker) : StatusBarWidget, StatusBarWidget.TextPresentation {
  private var statusBar: StatusBar? = null

  init {
    lightGitTracker.addUpdateListener(object : LightGitTrackerListener {
      override fun update() {
        statusBar?.updateWidget(ID())
      }
    }, this)
  }

  override fun ID(): String = "light.edit.git"

  override fun install(statusBar: StatusBar) {
    this.statusBar = statusBar
  }

  override fun getPresentation(): StatusBarWidget.WidgetPresentation? = this

  override fun getText(): String {
    return lightGitTracker.currentLocation?.let { "Git: $it" } ?: ""
  }

  override fun getTooltipText(): String? {
    val locationText = lightGitTracker.currentLocation?.let { "Current Git Branch: $it" } ?: ""
    if (locationText.isBlank()) return locationText

    val selectedFile = LightEditService.getInstance().selectedFile
    if (selectedFile != null) {
      val statusText = lightGitTracker.getFileStatus(selectedFile).getPresentation()
      if (statusText.isNotBlank()) return "$locationText<br/>$statusText"
    }
    return locationText
  }

  override fun getAlignment(): Float = Component.LEFT_ALIGNMENT

  override fun getClickConsumer(): Consumer<MouseEvent>? = null

  override fun dispose() = Unit
}

class LightGitStatusBarWidgetProvider : StatusBarWidgetProvider {
  override fun getWidget(project: Project): StatusBarWidget? {
    if (project != LightEditUtil.getProjectIfCreated()) return null
    return LightGitStatusBarWidget(LightGitTracker.getInstance())
  }

  override fun isCompatibleWith(frame: IdeFrame): Boolean = frame is LightEditFrame
}