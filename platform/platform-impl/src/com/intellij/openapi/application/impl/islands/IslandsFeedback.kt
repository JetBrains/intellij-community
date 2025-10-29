// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application.impl.islands

import com.intellij.icons.AllIcons
import com.intellij.ide.IdeBundle
import com.intellij.ide.ui.AppearanceConfigurable
import com.intellij.ide.ui.LafManager
import com.intellij.idea.AppMode
import com.intellij.notification.Notification
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.ui.ExperimentalUI
import java.lang.ref.WeakReference

internal class IslandsFeedback : ProjectActivity {
  internal companion object {
    internal fun isIslandTheme(): Boolean {
      return isIslandTheme(LafManager.getInstance().currentUIThemeLookAndFeel?.id ?: return false)
    }

    internal fun isIslandTheme(themeId: String) = themeId == "Islands Dark" || themeId == "Islands Light"

    @Volatile
    private var myFirstProject = true
  }

  override suspend fun execute(project: Project) {
    if (myFirstProject) {
      myFirstProject = false

      if (!ApplicationManager.getApplication().isUnitTestMode &&
          !ApplicationManager.getApplication().isHeadlessEnvironment &&
          !AppMode.isRemoteDevHost() && ExperimentalUI.isNewUI()) {

        handleFeedback(project)
      }
    }
  }
}

private fun handleFeedback(project: Project) {
}

private fun showPromoNotification(projectRef: WeakReference<Project>) {
  val notification = Notification("STICKY:Feedback In IDE", IdeBundle.message("ide.islands.share.feedback.promo.title"),
                                  IdeBundle.message("ide.islands.share.feedback.promo.message"), NotificationType.INFORMATION)

  notification.addAction(NotificationAction.createSimpleExpiring(IdeBundle.message("got.it.button.name")) {})

  notification.addAction(NotificationAction.createSimpleExpiring(IdeBundle.message("ide.islands.read.more")) {
    //BrowserUtil.browse(IslandsFeedback.getReadMoreUrl())
  })

  notification.addAction(NotificationAction.createSimpleExpiring(IdeBundle.message("ide.islands.switch.theme")) {
    ShowSettingsUtil.getInstance().showSettingsDialog(projectRef.get(), AppearanceConfigurable::class.java)
  })

  notification.setSuggestionType(true).setImportantSuggestion(true).setIcon(AllIcons.Ide.Gift).setAddExtraAction(true).notify(null)
}