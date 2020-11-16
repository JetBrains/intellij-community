package org.jetbrains.kotlin.idea.configuration.ui.notifications

import com.intellij.notification.*
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.components.*
import com.intellij.openapi.externalSystem.importing.ImportSpecBuilder
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import org.jetbrains.kotlin.idea.KotlinBundle
import org.jetbrains.kotlin.idea.PlatformVersion
import org.jetbrains.plugins.gradle.settings.GradleProjectSettings
import org.jetbrains.plugins.gradle.settings.GradleSettings
import org.jetbrains.plugins.gradle.util.GradleConstants

fun notifyLegacyIsResolveModulePerSourceSetSettingIfNeeded(projectPath: String) {
    notifyLegacyIsResolveModulePerSourceSetSettingIfNeeded(
        ProjectManager.getInstance().openProjects.firstOrNull { it.basePath == projectPath } ?: return
    )
}

fun notifyLegacyIsResolveModulePerSourceSetSettingIfNeeded(project: Project) {
    if (ApplicationManager.getApplication().isUnitTestMode) return
    if (project.isResolveModulePerSourceSetNotificationSuppressed) return
    if (project.gradleProjectSettings.all { it.isResolveModulePerSourceSet }) return
    if (PlatformVersion.isAndroidStudio()) return

    createNotification().notify(project)
}

private fun createNotification(): Notification {
    NotificationsConfiguration.getNotificationsConfiguration().register(
        KOTLIN_UPDATE_IS_RESOLVE_MODULE_PER_SOURCE_SET_GROUP_ID,
        NotificationDisplayType.STICKY_BALLOON,
        true
    )
    return Notification(
        KOTLIN_UPDATE_IS_RESOLVE_MODULE_PER_SOURCE_SET_GROUP_ID,
        KotlinBundle.message("configuration.is.resolve.module.per.source.set"),
        KotlinBundle.htmlMessage("configuration.update.is.resolve.module.per.source.set"),
        NotificationType.WARNING
    ).apply {
        addActions(listOf(createUpdateGradleProjectSettingsAction(), createSuppressNotificationAction()))
        isImportant = true
    }
}

private fun createUpdateGradleProjectSettingsAction() = NotificationAction.create(
    KotlinBundle.message("configuration.apply.is.resolve.module.per.source.set"),
) { event: AnActionEvent, notification: Notification ->
    notification.expire()
    val project = event.project ?: return@create
    if (project.isDisposed) return@create

    runWriteAction {
        project.gradleProjectSettings.forEach { it.isResolveModulePerSourceSet = true }
        ExternalSystemUtil.refreshProjects(ImportSpecBuilder(project, GradleConstants.SYSTEM_ID))
    }
}

private fun createSuppressNotificationAction() = NotificationAction.create(
    KotlinBundle.message("configuration.do.not.suggest.update.is.resolve.module.per.source.set")
) { event: AnActionEvent, notification: Notification ->
    notification.expire()
    val project = event.project ?: return@create
    if (project.isDisposed) return@create
    runWriteAction {
        project.isResolveModulePerSourceSetNotificationSuppressed = true
    }
}

private val Project.gradleProjectSettings: List<GradleProjectSettings>
    get() = GradleSettings.getInstance(this).linkedProjectsSettings.toList()

private var Project.isResolveModulePerSourceSetNotificationSuppressed: Boolean
    get() = ResolveModulePerSourceSetComponent.getInstance(this).state.disableForAll
    set(value) {
        ResolveModulePerSourceSetComponent.getInstance(this).state.disableForAll = value
    }

private class SuppressResolveModulePerSourceSetNotificationState : BaseState() {
    var disableForAll: Boolean by property(false)
}

@Service
@State(name = "SuppressResolveModulePerSourceSetNotification")
private class ResolveModulePerSourceSetComponent :
    SimplePersistentStateComponent<SuppressResolveModulePerSourceSetNotificationState>(
        SuppressResolveModulePerSourceSetNotificationState()
    ) {
    companion object {
        fun getInstance(project: Project): ResolveModulePerSourceSetComponent = project.service()
    }
}

private const val KOTLIN_UPDATE_IS_RESOLVE_MODULE_PER_SOURCE_SET_GROUP_ID =
    "Update isResolveModulePerSourceSet setting"
