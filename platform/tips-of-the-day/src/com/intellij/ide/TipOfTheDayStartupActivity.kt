// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide

import com.intellij.ide.util.TipAndTrickManager
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.extensions.ExtensionNotApplicableException
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.wm.IdeFrame
import com.intellij.openapi.wm.WindowManager
import com.intellij.util.PlatformUtils

private class TipOfTheDayStartupActivity : ProjectActivity {
  init {
    if (ApplicationManager.getApplication().isHeadlessEnvironment ||
        PlatformUtils.isRider() ||
        !GeneralSettings.getInstance().isShowTipsOnStartup) {
      throw ExtensionNotApplicableException.create()
    }
  }

  override suspend fun execute(project: Project) {
    val tipManager = serviceAsync<TipAndTrickManager>()
    if (tipManager.canShowDialogAutomaticallyNow(project)
        // prevent tip dialog showing when any popup already open
        && WindowManager.getInstance().mostRecentFocusedWindow is IdeFrame) {
      TipsOfTheDayUsagesCollector.triggerDialogShown(TipsOfTheDayUsagesCollector.DialogType.automatically)
      tipManager.showTipDialog(project)
    }
  }
}