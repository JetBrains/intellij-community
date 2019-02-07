// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm.impl

import com.intellij.configurationStore.StoreUtil
import com.intellij.ide.AppLifecycleListener
import com.intellij.ide.GeneralSettings
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ex.ApplicationManagerEx
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.project.ex.ProjectManagerEx
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.wm.impl.welcomeScreen.WelcomeFrame
import com.intellij.util.SystemProperties

open class CloseProjectWindowHelper {
  protected open val isMacSystemMenu: Boolean
    get() = SystemProperties.getBooleanProperty("idea.test.isMacSystemMenu", SystemInfo.isMacSystemMenu)

  private val isShowWelcomeScreen: Boolean
    get() = isMacSystemMenu && isShowWelcomeScreenFromSettings

  protected open val isShowWelcomeScreenFromSettings
    get() = GeneralSettings.getInstance().isShowWelcomeScreen

  fun windowClosing(project: Project?) {
    val numberOfOpenedProjects = getNumberOfOpenedProjects()
    // Exit on Linux and Windows if the only opened project frame is closed.
    // On macOS behaviour is different - to exit app, quit action should be used, otherwise welcome frame is shown.
    // If welcome screen is disabled, behaviour on all OS is the same.
    if (numberOfOpenedProjects > 1 || (numberOfOpenedProjects == 1 && isShowWelcomeScreen)) {
      closeProjectAndShowWelcomeFrameIfNoProjectOpened(project)
    }
    else {
      quitApp()
    }
  }

  protected open fun getNumberOfOpenedProjects() = ProjectManager.getInstance().openProjects.size

  protected open fun closeProjectAndShowWelcomeFrameIfNoProjectOpened(project: Project?) {
    if (project != null && project.isOpen) {
      ProjectManagerEx.getInstanceEx().closeAndDispose(project)
    }

    val app = ApplicationManager.getApplication()
    app.messageBus.syncPublisher(AppLifecycleListener.TOPIC).projectFrameClosed()
    StoreUtil.saveSettings(app, true)

    WelcomeFrame.showIfNoProjectOpened()
  }

  protected open fun quitApp() {
    ApplicationManagerEx.getApplicationEx().exit()
  }
}