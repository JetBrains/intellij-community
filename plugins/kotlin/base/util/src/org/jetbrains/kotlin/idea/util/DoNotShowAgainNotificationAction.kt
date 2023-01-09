// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.util

import com.intellij.ide.IdeBundle
import com.intellij.ide.util.PropertiesComponent
import com.intellij.notification.Notification
import com.intellij.notification.NotificationAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.ApiStatus

/**
 * Needed as a hack until ^IDEA-306920 is fixed, after the fix should be moved to KotlinJsCompilerNotificationImportListener
 */
@ApiStatus.Internal
class DoNotShowAgainNotificationAction(
    private val project: Project,
    private val notificationId: String,
) : NotificationAction(IdeBundle.message("action.Anonymous.text.do.not.show.again")) {
    override fun actionPerformed(e: AnActionEvent, notification: Notification) {
        PropertiesComponent.getInstance(project).setValue(notificationId, true)
        notification.expire()
    }
}