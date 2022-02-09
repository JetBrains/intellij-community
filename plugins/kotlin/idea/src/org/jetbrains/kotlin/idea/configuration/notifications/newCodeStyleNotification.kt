// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.configuration.notifications

import com.intellij.application.options.CodeStyle
import com.intellij.ide.IdeCoreBundle
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.components.*
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.idea.KotlinBundle
import org.jetbrains.kotlin.idea.KotlinIcons
import org.jetbrains.kotlin.idea.formatter.KotlinStyleGuideCodeStyle
import org.jetbrains.kotlin.idea.formatter.ProjectCodeStyleImporter
import org.jetbrains.kotlin.idea.formatter.kotlinCodeStyleDefaults

internal fun notifyKotlinStyleUpdateIfNeeded(project: Project) {
    if (CodeStyle.getSettings(project).kotlinCodeStyleDefaults() == KotlinStyleGuideCodeStyle.CODE_STYLE_ID) return
    if (SuppressKotlinCodeStyleComponent.getInstance(project).state.disableForAll) {
        return
    }

    NotificationGroupManager.getInstance()
        .getNotificationGroup("Update Kotlin code style")
        .createNotification(
            KotlinBundle.message("configuration.kotlin.code.style"),
            KotlinBundle.htmlMessage("configuration.notification.update.code.style.to.official"),
            NotificationType.WARNING,
        )
        .setSuggestionType(true)
        .addAction(applyCodeStyleAction())
        .addAction(dontAskAgainAction())
        .setImportant(true)
        .setIcon(KotlinIcons.SMALL_LOGO)
        .notify(project)
}

private fun dontAskAgainAction() = NotificationAction.createExpiring(
    IdeCoreBundle.message("dialog.options.do.not.ask")
) { e, _ ->
    e.project?.takeIf { !it.isDisposed }?.let { project ->
        runWriteAction {
            SuppressKotlinCodeStyleComponent.getInstance(project).state.disableForAll = true
        }
    }
}

private fun applyCodeStyleAction() = NotificationAction.createExpiring(
    KotlinBundle.message("configuration.apply.new.code.style")
) { e, _ ->
    e.project?.takeIf { !it.isDisposed }?.let { project ->
        runWriteAction {
            ProjectCodeStyleImporter.apply(project, KotlinStyleGuideCodeStyle.INSTANCE)
        }
    }
}

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
