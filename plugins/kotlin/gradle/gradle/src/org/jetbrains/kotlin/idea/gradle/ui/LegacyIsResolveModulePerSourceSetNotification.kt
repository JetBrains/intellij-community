// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.gradle.ui

import com.intellij.notification.*
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.components.*
import com.intellij.openapi.externalSystem.importing.ImportSpecBuilder
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.util.PlatformUtils
import org.jetbrains.kotlin.idea.base.util.KotlinPlatformUtils
import org.jetbrains.kotlin.idea.gradle.KotlinIdeaGradleBundle
import org.jetbrains.plugins.gradle.settings.GradleProjectSettings
import org.jetbrains.plugins.gradle.settings.GradleSettings
import org.jetbrains.plugins.gradle.util.GradleConstants
import java.lang.ref.WeakReference

private var previouslyShownNotification = WeakReference<Notification>(null)

fun notifyLegacyIsResolveModulePerSourceSetSettingIfNeeded(projectPath: String) {
    notifyLegacyIsResolveModulePerSourceSetSettingIfNeeded(
        ProjectManager.getInstance().openProjects.firstOrNull { it.basePath == projectPath } ?: return
    )
}

fun notifyLegacyIsResolveModulePerSourceSetSettingIfNeeded(project: Project) {
    notifyLegacyIsResolveModulePerSourceSetSettingIfNeeded(
        project = project,
        notificationSuppressState = SuppressResolveModulePerSourceSetNotificationState.default(project),
        isResolveModulePerSourceSetSetting = IsResolveModulePerSourceSetSetting.default(project),
    )
}

fun notifyLegacyIsResolveModulePerSourceSetSettingIfNeeded(
    project: Project,
    notificationSuppressState: SuppressResolveModulePerSourceSetNotificationState,
    isResolveModulePerSourceSetSetting: IsResolveModulePerSourceSetSetting
) {
    if (KotlinPlatformUtils.isAndroidStudio) return
    if (notificationSuppressState.isSuppressed) return
    if (isResolveModulePerSourceSetSetting.isResolveModulePerSourceSet) return
    if (previouslyShownNotification.get()?.isExpired == false) return
    val notification = createNotification(notificationSuppressState, isResolveModulePerSourceSetSetting)
    notification.notify(project)
    previouslyShownNotification = WeakReference(notification)
}

private fun createNotification(
    notificationSuppressState: SuppressResolveModulePerSourceSetNotificationState,
    isResolveModulePerSourceSetSetting: IsResolveModulePerSourceSetSetting
): Notification {
    NotificationsConfiguration.getNotificationsConfiguration().register(
        KOTLIN_UPDATE_IS_RESOLVE_MODULE_PER_SOURCE_SET_GROUP_ID,
        NotificationDisplayType.STICKY_BALLOON,
        true
    )
    return Notification(
        KOTLIN_UPDATE_IS_RESOLVE_MODULE_PER_SOURCE_SET_GROUP_ID,
        KotlinIdeaGradleBundle.message("configuration.is.resolve.module.per.source.set"),
        KotlinIdeaGradleBundle.htmlMessage("configuration.update.is.resolve.module.per.source.set"),
        NotificationType.WARNING
    ).apply {
        isSuggestionType = true
        addAction(createUpdateGradleProjectSettingsAction(isResolveModulePerSourceSetSetting))
        addAction(createSuppressNotificationAction(notificationSuppressState))
        isImportant = true
    }
}

private fun createUpdateGradleProjectSettingsAction(
    isResolveModulePerSourceSetSetting: IsResolveModulePerSourceSetSetting
) = NotificationAction.create(
    KotlinIdeaGradleBundle.message("configuration.apply.is.resolve.module.per.source.set"),
) { event: AnActionEvent, notification: Notification ->
    notification.expire()
    val project = event.project ?: return@create
    if (project.isDisposed) return@create

    runWriteAction {
        isResolveModulePerSourceSetSetting.isResolveModulePerSourceSet = true
        ExternalSystemUtil.refreshProjects(ImportSpecBuilder(project, GradleConstants.SYSTEM_ID))
    }
}

private fun createSuppressNotificationAction(
    notificationSuppressState: SuppressResolveModulePerSourceSetNotificationState
) = NotificationAction.create(
    KotlinIdeaGradleBundle.message("configuration.do.not.suggest.update.is.resolve.module.per.source.set")
) { event: AnActionEvent, notification: Notification ->
    notification.expire()
    val project = event.project ?: return@create
    if (project.isDisposed) return@create
    runWriteAction {
        notificationSuppressState.isSuppressed = true
    }
}

private val Project.gradleProjectSettings: List<GradleProjectSettings>
    get() = GradleSettings.getInstance(this).linkedProjectsSettings.toList()


/*
Accessing "isResolveModulePerSourceSet" setting
 */

interface IsResolveModulePerSourceSetSetting {
    var isResolveModulePerSourceSet: Boolean

    companion object {
        fun default(project: Project): IsResolveModulePerSourceSetSetting = ProjectIsResolveModulePerSourceSetSetting(project)
    }
}

private class ProjectIsResolveModulePerSourceSetSetting(private val project: Project) : IsResolveModulePerSourceSetSetting {
    override var isResolveModulePerSourceSet: Boolean
        get() = project.gradleProjectSettings.all { it.isResolveModulePerSourceSet }
        set(value) = project.gradleProjectSettings.forEach { it.isResolveModulePerSourceSet = value }
}


/*
Storing State about Notification Suppress
 */

interface SuppressResolveModulePerSourceSetNotificationState {
    var isSuppressed: Boolean

    companion object {
        fun default(project: Project): SuppressResolveModulePerSourceSetNotificationState =
            IdeResolveModulePerSourceSetComponent.getInstance(project).state
    }
}

private class IdeSuppressResolveModulePerSourceSetNotificationState : BaseState(), SuppressResolveModulePerSourceSetNotificationState {
    override var isSuppressed: Boolean by property(false)
}

@Service(Service.Level.PROJECT)
@State(name = "SuppressResolveModulePerSourceSetNotification")
private class IdeResolveModulePerSourceSetComponent :
    SimplePersistentStateComponent<IdeSuppressResolveModulePerSourceSetNotificationState>(
        IdeSuppressResolveModulePerSourceSetNotificationState()
    ) {
    companion object {
        fun getInstance(project: Project): IdeResolveModulePerSourceSetComponent = project.service()
    }
}

const val KOTLIN_UPDATE_IS_RESOLVE_MODULE_PER_SOURCE_SET_GROUP_ID = "Update isResolveModulePerSourceSet setting"
