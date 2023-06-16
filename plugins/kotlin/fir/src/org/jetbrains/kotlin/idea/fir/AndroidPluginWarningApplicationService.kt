// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.fir

import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.notification.Notification
import com.intellij.notification.NotificationDisplayType
import com.intellij.notification.NotificationType
import com.intellij.notification.NotificationsConfiguration
import com.intellij.openapi.extensions.ExtensionNotApplicableException
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.util.NlsSafe
import org.jetbrains.kotlin.idea.base.util.KotlinPlatformUtils

private class AndroidPluginIncompatibilityCheckerStartupActivity : ProjectActivity {
    init {
      if (KotlinPlatformUtils.isAndroidStudio) {
          throw ExtensionNotApplicableException.create()
      }
    }

    override suspend fun execute(project: Project) {
        NotificationsConfiguration.getNotificationsConfiguration()
            .register(
                AndroidPluginWarningNotification.ID,
                NotificationDisplayType.STICKY_BALLOON,
                true
            )
        if (PluginManagerCore.getPlugin(PluginId.getId(ANDROID_PLUGIN_ID))?.isEnabled == true) {
            AndroidPluginWarningNotification().notify(project)
        }
    }

    companion object {
        private const val ANDROID_PLUGIN_ID = "org.jetbrains.android"
    }
}


private class AndroidPluginWarningNotification : Notification(
    ID,
    ID,
    message,
    NotificationType.ERROR,
) {
    companion object {
        @NlsSafe
        val ID = "Android Plugin is incompatible with FIR IDE"

        @NlsSafe
        private const val message =
            "Android Plugin is incompatible with FIR IDE. Please, consider disabling Android plugin. Otherwise, Kotlin resolve may not work."
    }
}