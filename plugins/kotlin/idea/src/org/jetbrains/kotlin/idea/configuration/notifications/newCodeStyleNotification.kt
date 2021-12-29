// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.configuration.notifications

import com.intellij.application.options.CodeStyle
import com.intellij.notification.*
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.components.*
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.idea.KotlinBundle
import org.jetbrains.kotlin.idea.formatter.KotlinStyleGuideCodeStyle
import org.jetbrains.kotlin.idea.formatter.ProjectCodeStyleImporter
import org.jetbrains.kotlin.idea.formatter.kotlinCodeStyleDefaults

private const val KOTLIN_UPDATE_CODE_STYLE_GROUP_ID = "Update Kotlin code style"

internal fun notifyKotlinStyleUpdateIfNeeded(project: Project) {
    if (CodeStyle.getSettings(project).kotlinCodeStyleDefaults() == KotlinStyleGuideCodeStyle.CODE_STYLE_ID) return
    if (SuppressKotlinCodeStyleComponent.getInstance(project).state.disableForAll) {
        return
    }

    val notification = createNotification()
    NotificationsConfiguration.getNotificationsConfiguration().register(
        KOTLIN_UPDATE_CODE_STYLE_GROUP_ID,
        NotificationDisplayType.STICKY_BALLOON,
        true
    )

    notification.notify(project)
}

private fun createNotification(): Notification =
    Notification(
        KOTLIN_UPDATE_CODE_STYLE_GROUP_ID,
        KotlinBundle.message("configuration.kotlin.code.style"),
        KotlinBundle.htmlMessage("configuration.notification.update.code.style.to.official"),
        NotificationType.WARNING
    )
        .addAction(NotificationAction.createExpiring(KotlinBundle.message("configuration.apply.new.code.style")) { e, _ ->
            e.project?.takeIf { !it.isDisposed }?.let { project ->
                runWriteAction {
                    ProjectCodeStyleImporter.apply(project, KotlinStyleGuideCodeStyle.INSTANCE)
                }
            }
        })
        .addAction(NotificationAction.createExpiring(KotlinBundle.message("configuration.do.not.suggest.new.code.style")) { e, _ ->
            e.project?.takeIf { !it.isDisposed }?.let { project ->
                runWriteAction {
                    SuppressKotlinCodeStyleComponent.getInstance(project).state.disableForAll = true
                }
            }
        })
        .setImportant(true)

class SuppressKotlinCodeStyleState : BaseState() {
    var disableForAll by property(false)
}

@Service(Service.Level.PROJECT)
@State(name = "SuppressKotlinCodeStyleNotification")
class SuppressKotlinCodeStyleComponent : SimplePersistentStateComponent<SuppressKotlinCodeStyleState>(SuppressKotlinCodeStyleState()) {
    companion object {
        fun getInstance(project: Project): SuppressKotlinCodeStyleComponent = project.service()
    }
}
