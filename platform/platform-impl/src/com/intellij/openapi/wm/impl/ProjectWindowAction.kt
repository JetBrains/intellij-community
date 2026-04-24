// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl

import com.intellij.ide.IdeBundle
import com.intellij.ide.lightEdit.LightEditService
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.KeepPopupOnPerform
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.SystemInfoRt
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.openapi.wm.WindowManager
import com.intellij.project.ProjectStoreOwner
import com.intellij.util.BitUtil.isSet
import com.intellij.util.BitUtil.set
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.NonNls
import java.awt.Frame
import java.awt.event.KeyEvent
import java.nio.file.Path

/**
 * This class is programmatically instantiated and registered when opening and closing projects and therefore not registered in plugin.xml
 */
@ApiStatus.Internal
class ProjectWindowAction(
  @param:NlsSafe val projectName: @NlsSafe String,
  val projectLocation: Path,
  previous: ProjectWindowAction?,
  val excludedFromProjectWindowSwitchOrder: Boolean = false,
) : ToggleAction(IdeBundle.message("action.switch.project.text")), DumbAware {
  private var myPrevious: ProjectWindowAction? = null
  private var myNext: ProjectWindowAction? = null

  init {
    if (previous == null) {
      myPrevious = this
      myNext = this
    }
    else {
      myPrevious = previous
      myNext = previous.myNext
      myNext!!.myPrevious = this
      myPrevious!!.myNext = this
    }
    getTemplatePresentation().setText(projectName, false)
    getTemplatePresentation().setKeepPopupOnPerform(KeepPopupOnPerform.Never)
  }

  fun dispose() {
    if (myPrevious == this) {
      assert(myNext == this)
      return
    }

    if (myNext == this) {
      assert(false)
      return
    }

    myPrevious?.myNext = myNext
    myNext?.myPrevious = myPrevious
  }

  val previous: ProjectWindowAction?
    get() = myPrevious

  val next: ProjectWindowAction?
    get() = myNext

  private fun findProject(): Project? {
    if (LightEditService.windowName == this.projectName) {
      return LightEditService.getInstance().project
    }

    val projects = ProjectManager.getInstance().getOpenProjects()
    for (project in projects) {
      if (projectLocation == (project as ProjectStoreOwner).componentStore.storeDescriptor.presentableUrl) {
        return project
      }
    }
    return null
  }

  override fun isSelected(e: AnActionEvent): Boolean {
    // show check mark for active and visible project frame
    val project = e.getData(CommonDataKeys.PROJECT) as? ProjectStoreOwner ?: return false
    return projectLocation == project.componentStore.storeDescriptor.presentableUrl
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun setSelected(e: AnActionEvent, selected: Boolean) {
    val project = findProject() ?: return
    val projectFrame = WindowManager.getInstance().getFrame(project) ?: return
    val frameState = projectFrame.extendedState
    if (SystemInfoRt.isMac && isSet(projectFrame.extendedState, Frame.ICONIFIED) && e.inputEvent is KeyEvent) {
      // On Mac minimized window should not be restored this way
      return
    }

    if (isSet(frameState, Frame.ICONIFIED)) {
      // restore the frame if it is minimized
      projectFrame.setExtendedState(set(frameState, Frame.ICONIFIED, false))
    }

    projectFrame.toFront()
    IdeFocusManager.getGlobalInstance().doWhenFocusSettlesDown {
      val mostRecentFocusOwner = projectFrame.mostRecentFocusOwner
      if (mostRecentFocusOwner != null) {
        IdeFocusManager.getGlobalInstance().requestFocus(mostRecentFocusOwner, true)
      }
    }
  }

  @NonNls
  override fun toString(): @NonNls String {
    return (getTemplatePresentation().getText()
            + " previous: " + myPrevious!!.getTemplatePresentation().getText()
            + " next: " + myNext!!.getTemplatePresentation().getText())
  }
}
