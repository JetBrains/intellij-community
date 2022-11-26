// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.gradleJava.notification

import com.intellij.ide.IdeBundle
import com.intellij.ide.util.PropertiesComponent
import com.intellij.notification.*
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.externalSystem.service.project.manage.ProjectDataImportListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Condition
import org.jetbrains.kotlin.idea.core.KotlinPluginDisposable
import org.jetbrains.kotlin.idea.gradle.KotlinIdeaGradleBundle

const val IGNORE_KOTLIN_JS_COMPILER_NOTIFICATION = "notification.kotlin.js.compiler.ignored"
const val KOTLIN_JS_COMPILER_SHOULD_BE_NOTIFIED = "notification.kotlin.js.compiler.should.be.notified"

class KotlinJsCompilerNotificationImportListener(private val project: Project) : ProjectDataImportListener {
    override fun onImportFinished(projectPath: String?) {
        ApplicationManager.getApplication().invokeLater(
            Runnable {
                val hasJsTarget = PropertiesComponent.getInstance(project).getBoolean(KOTLIN_JS_COMPILER_SHOULD_BE_NOTIFIED, false)
                if (!hasJsTarget) return@Runnable
                PropertiesComponent.getInstance(project).unsetValue(KOTLIN_JS_COMPILER_SHOULD_BE_NOTIFIED)
                showDeprecatedKotlinJsCompilerWarning(project)
            },
            Condition<Any?> { project.isDisposed || KotlinPluginDisposable.getInstance(project).disposed },
        )
    }
}

fun showDeprecatedKotlinJsCompilerWarning(
    project: Project,
) {
    if (
        !PropertiesComponent.getInstance(project).getBoolean(IGNORE_KOTLIN_JS_COMPILER_NOTIFICATION, false)
    ) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup("Kotlin/JS compiler Gradle")
            .createNotification(
                KotlinIdeaGradleBundle.message("notification.text.kotlin.js.compiler.title"),
                KotlinIdeaGradleBundle.message("notification.text.kotlin.js.compiler.body"),
                NotificationType.WARNING
            )
            .addAction(
                BrowseNotificationAction(
                    KotlinIdeaGradleBundle.message("notification.text.kotlin.js.compiler.learn.more"),
                    KotlinIdeaGradleBundle.message("notification.text.kotlin.js.compiler.link"),
                )
            )
            .addAction(object : NotificationAction(IdeBundle.message("action.Anonymous.text.do.not.show.again")) {
                override fun actionPerformed(e: AnActionEvent, notification: Notification) {
                    PropertiesComponent.getInstance(project).setValue(IGNORE_KOTLIN_JS_COMPILER_NOTIFICATION, true)
                    notification.expire();
                }
            })
            .notify(project)

    }
}
