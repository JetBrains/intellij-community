// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.gradleJava.scripting

import com.intellij.notification.NotificationType
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemJdkUtil
import com.intellij.openapi.externalSystem.service.execution.InvalidSdkException
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.JdkUtil
import com.intellij.openapi.startup.StartupActivity
import org.jetbrains.kotlin.idea.gradle.KotlinIdeaGradleBundle
import org.jetbrains.kotlin.idea.gradleJava.KotlinGradleNotificationIdsHolder
import org.jetbrains.plugins.gradle.service.project.GradleNotification.NOTIFICATION_GROUP
import org.jetbrains.plugins.gradle.settings.GradleProjectSettings
import org.jetbrains.plugins.gradle.settings.GradleSettings

class SdkValidator : StartupActivity {
    override fun runActivity(project: Project) {
        GradleSettings.getInstance(project).linkedProjectsSettings.forEach {
            it.validateGradleSdk(project)
        }
    }
}

fun GradleProjectSettings.validateGradleSdk(project: Project, jdkHomePath: String? = null) {
    val gradleJvm = gradleJvm ?: return

    var jdkName: String? = null

    var message: String? = null

    val homePath = if (jdkHomePath != null) {
        jdkHomePath
    } else {
        // gradleJvm could be #USE_PROJECT_JDK etc, see ExternalSystemJdkUtil
        val jdk = try {
            ExternalSystemJdkUtil.getJdk(project, gradleJvm)
        } catch (e: InvalidSdkException) {
            message = e.message
            null
        } catch (e: Exception) {
            null
        }

        jdkName = jdk?.name
        jdk?.homePath
    }

    if (message == null) {
        message = when {
            homePath == null -> {
                KotlinIdeaGradleBundle.message("notification.gradle.jvm.undefined")
            }
            !JdkUtil.checkForJdk(homePath) -> {
                jdkName?.let { KotlinIdeaGradleBundle.message("notification.jdk.0.points.to.invalid.jdk", it) }
                    ?: KotlinIdeaGradleBundle.message("notification.gradle.jvm.0.incorrect", homePath)
            }
            else -> null
        }
    }

    message?.let {
        NOTIFICATION_GROUP.createNotification(KotlinIdeaGradleBundle.message("notification.invalid.gradle.jvm.configuration.title"), message, NotificationType.ERROR)
            .setDisplayId(KotlinGradleNotificationIdsHolder.kotlinScriptingJvmInvalid)
            .notify(project)
    }

}
