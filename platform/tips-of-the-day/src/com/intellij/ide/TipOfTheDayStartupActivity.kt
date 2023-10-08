// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide

import com.intellij.ide.util.TipAndTrickManager
import com.intellij.notification.impl.NotificationsStateWatcher
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.extensions.ExtensionNotApplicableException
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.wm.IdeFrame
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.WindowManager
import com.intellij.util.PlatformUtils
import kotlinx.coroutines.suspendCancellableCoroutine

private class TipOfTheDayStartupActivity : ProjectActivity {
  init {
    if (ApplicationManager.getApplication().isHeadlessEnvironment ||
        ApplicationManager.getApplication().isUnitTestMode ||
        PlatformUtils.isRider() ||
        !GeneralSettings.getInstance().isShowTipsOnStartup) {
      throw ExtensionNotApplicableException.create()
    }
  }

  override suspend fun execute(project: Project) {
    awaitToolwindowManager(project)

    val tipManager = serviceAsync<TipAndTrickManager>()
    if (tipManager.canShowDialogAutomaticallyNow(project)
        && !hasSuggestionNotifications(project)
        // prevent tip dialog showing when any popup already open
        && WindowManager.getInstance().mostRecentFocusedWindow is IdeFrame) {
      TipsOfTheDayUsagesCollector.triggerDialogShown(TipsOfTheDayUsagesCollector.DialogType.automatically)
      tipManager.showTipDialog(project)
    }
  }

  private suspend fun awaitToolwindowManager(project: Project) {
    return suspendCancellableCoroutine { continuation ->
      ToolWindowManager.getInstance(project).invokeLater(Runnable {
        continuation.resumeWith(Result.success(Unit))
      })
    }
  }

  private fun hasSuggestionNotifications(project: Project): Boolean {
    val hasSuggestions = NotificationsStateWatcher.hasSuggestionNotifications(project)
    if (hasSuggestions) {
      thisLogger().info("Skipping Tip-Of-The-Day because there are suggestion notifications shown")
    }
    return hasSuggestions
  }
}