// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm.impl

import com.intellij.configurationStore.runInAutoSaveDisabledMode
import com.intellij.ide.AppLifecycleListener
import com.intellij.ide.GeneralSettings
import com.intellij.ide.SaveAndSyncHandler
import com.intellij.ide.lightEdit.LightEditService
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.wm.impl.welcomeScreen.WelcomeFrame
import com.intellij.util.PlatformUtils
import com.intellij.util.SystemProperties

open class CloseProjectWindowHelper {
  companion object {
    /** This key may be used to for a specific behaviour when project is closing for particular projects */
    @JvmStatic
    val SHOW_WELCOME_FRAME_FOR_PROJECT: Key<Boolean> = Key.create("Show.Welcome.Frame.For.Project")
  }

  protected open val isMacSystemMenu: Boolean
    get() = SystemProperties.getBooleanProperty("idea.test.isMacSystemMenu", SystemInfo.isMacSystemMenu)

  private val isShowWelcomeScreen: Boolean
    get() = isMacSystemMenu && isShowWelcomeScreenFromSettings

  protected open val isShowWelcomeScreenFromSettings
    get() = GeneralSettings.getInstance().isShowWelcomeScreen

  open fun windowClosing(project: Project?) {
    val numberOfOpenedProjects = getNumberOfOpenedProjects()
    val isLightEditActive = LightEditService.getInstance().project != null;
    // Exit on Linux and Windows if the only opened project frame is closed.
    // On macOS behaviour is different - to exit app, quit action should be used, otherwise welcome frame is shown.
    // If welcome screen is disabled, behaviour on all OS is the same.
    if (numberOfOpenedProjects > 1 || isLightEditActive || (numberOfOpenedProjects == 1 && couldReturnToWelcomeScreen(project))) {
      closeProjectAndShowWelcomeFrameIfNoProjectOpened(project)
    }
    else {
      quitApp()
    }
  }

  protected open fun getNumberOfOpenedProjects() = ProjectManager.getInstance().openProjects.size

  protected open fun closeProjectAndShowWelcomeFrameIfNoProjectOpened(project: Project?) {
    runInAutoSaveDisabledMode {
      if (project != null && project.isOpen) {
        ProjectManager.getInstance().closeAndDispose(project)
      }

      val app = ApplicationManager.getApplication()
      app.messageBus.syncPublisher(AppLifecycleListener.TOPIC).projectFrameClosed()
      // app must be not saved as part of project closing because app settings maybe modified as result - e.g. RecentProjectsManager state
      SaveAndSyncHandler.getInstance().saveSettingsUnderModalProgress(app)
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