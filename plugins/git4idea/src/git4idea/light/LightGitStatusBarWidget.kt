// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.light

import com.intellij.ide.lightEdit.LightEditService
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.*
import com.intellij.util.Consumer
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
    return lightGitTracker.currentBranch?.let { "Git: $it" } ?: ""
  }

  override fun getTooltipText(): String? {
    return lightGitTracker.currentBranch?.let { "Current Git Branch: $it" } ?: ""
  }

  override fun getAlignment(): Float = Component.LEFT_ALIGNMENT

  override fun getClickConsumer(): Consumer<MouseEvent>? = null

  override fun dispose() = Unit
}

class LightGitStatusBarWidgetProvider: StatusBarWidgetProvider {
  override fun getWidget(project: Project): StatusBarWidget? {
    val lightEditorManager = LightEditService.getInstance().editorManager
    return LightGitStatusBarWidget(LightGitTracker(lightEditorManager))
  }

  override fun isCompatibleWith(frame: IdeFrame): Boolean = frame is LightEditFrame
}