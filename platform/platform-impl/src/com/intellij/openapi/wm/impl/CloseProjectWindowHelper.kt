// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl

import com.intellij.configurationStore.runInAutoSaveDisabledMode
import com.intellij.ide.AppLifecycleListener
import com.intellij.ide.GeneralSettings
import com.intellij.ide.SaveAndSyncHandler
import com.intellij.ide.lightEdit.LightEditService
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.WriteIntentReadAction
import com.intellij.openapi.components.serviceIfCreated
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.wm.WindowManager
import com.intellij.openapi.wm.impl.welcomeScreen.WelcomeFrame
import com.intellij.ui.mac.MacMenuSettings
import com.intellij.ui.mac.MergeAllWindowsAction
import com.intellij.ui.mac.WindowTabsComponent
import com.intellij.util.PlatformUtils
import com.intellij.util.SystemProperties
import com.intellij.util.concurrency.annotations.RequiresEdt
import org.jetbrains.annotations.ApiStatus

open class CloseProjectWindowHelper {
  companion object {
    /** This key may be used to for a specific behaviour when project is closing for particular projects */
    val SHOW_WELCOME_FRAME_FOR_PROJECT: Key<Boolean> = Key.create("Show.Welcome.Frame.For.Project")
  }

  protected open val isMacSystemMenu: Boolean
    get() = SystemProperties.getBooleanProperty("idea.test.isMacSystemMenu", MacMenuSettings.isSystemMenu)

  private val isShowWelcomeScreen: Boolean
    get() = isMacSystemMenu && isShowWelcomeScreenFromSettings

  protected open val isShowWelcomeScreenFromSettings: Boolean
    get() = GeneralSettings.getInstance().isShowWelcomeScreen

  @ApiStatus.Internal
  protected open fun couldReturnToWelcomeScreen(projects: Array<Project>): Boolean {
    return projects.any { project -> couldReturnToWelcomeScreen(project) }
  }

  @ApiStatus.Internal
  protected open fun isMacOsTabbedProjectView(project: Project?): Boolean {
    if (!SystemInfo.isMac) {
      return false
    }
    val projectFrame = WindowManager.getInstance().getFrame(project) ?: return false
    return MergeAllWindowsAction.isTabbedWindow(projectFrame)
  }

  @ApiStatus.Internal
  protected open fun isCloseTab(project: Project?): Boolean {
    project ?: return false

    val frame = WindowManager.getInstance().getFrame(project) ?: return false
    return frame.rootPane.getClientProperty(WindowTabsComponent.CLOSE_TAB_KEY) == true
  }

  @RequiresEdt
  open fun windowClosing(project: Project?) {
    WriteIntentReadAction.run {

      val numberOfOpenedProjects = getNumberOfOpenedProjects()
      val isMacOsTabbedProjectView = isMacOsTabbedProjectView(project)

      // Exit on Linux and Windows if the only opened project frame is closed.
      // On macOS behavior is different - to exit app, quit action should be used, otherwise welcome frame is shown.
      // If the Welcome screen is disabled, behavior on all OS is the same.
      if (!isMacOsTabbedProjectView && numberOfOpenedProjects > 1 ||
          isMacOsTabbedProjectView && isCloseTab(project) ||
          isMacOsTabbedProjectView && couldReturnToWelcomeScreen(projects = WindowManager.getInstance().allProjectFrames.mapNotNull { it.project }.toTypedArray()) ||
          serviceIfCreated<LightEditService>()?.project != null ||
          (numberOfOpenedProjects == 1 && couldReturnToWelcomeScreen(project))
        ) {
        closeProjectAndShowWelcomeFrameIfNoProjectOpened(project)
      }
      else {
        quitApp()
      }
    }
  }

  protected open fun getNumberOfOpenedProjects(): Int = ProjectManager.getInstance().openProjects.size

  @RequiresEdt
  protected open fun closeProjectAndShowWelcomeFrameIfNoProjectOpened(project: Project?) {
    runInAutoSaveDisabledMode {
      if (project != null && project.isOpen) {
        ProjectManager.getInstance().closeAndDispose(project)
      }
      ApplicationManager.getApplication().messageBus.syncPublisher(AppLifecycleListener.TOPIC).projectFrameClosed()
      SaveAndSyncHandler.getInstance().scheduleSave(task = SaveAndSyncHandler.SaveTask(forceSavingAllSettings = true),
                                                    forceExecuteImmediately = true)
    }
    WelcomeFrame.showIfNoProjectOpened()
  }

  protected open fun quitApp() {
    ApplicationManager.getApplication().exit()
  }

  private fun couldReturnToWelcomeScreen(project: Project?): Boolean {
    return project?.let { SHOW_WELCOME_FRAME_FOR_PROJECT.get(project) }
           ?: (isShowWelcomeScreen && !PlatformUtils.isDataSpell() && !PlatformUtils.isDataGrip())
  }

}
