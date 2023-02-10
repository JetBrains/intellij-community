// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.gradleJava.configuration.mpp

import com.intellij.build.events.MessageEvent
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.util.SystemInfo
import com.intellij.util.PlatformUtils
import org.jetbrains.kotlin.idea.base.util.KotlinPlatformUtils
import org.jetbrains.kotlin.idea.gradle.configuration.ResolveModulesPerSourceSetInMppBuildIssue
import org.jetbrains.kotlin.idea.gradle.configuration.suggestKotlinJsInspectionPackPlugin
import org.jetbrains.kotlin.idea.gradle.configuration.suggestNativeDebug
import org.jetbrains.kotlin.idea.gradle.ui.notifyLegacyIsResolveModulePerSourceSetSettingIfNeeded
import org.jetbrains.kotlin.idea.gradleJava.notification.IGNORE_KOTLIN_JS_COMPILER_NOTIFICATION
import org.jetbrains.kotlin.idea.gradleJava.notification.KOTLIN_JS_COMPILER_SHOULD_BE_NOTIFIED
import org.jetbrains.kotlin.idea.gradleTooling.KotlinGradlePluginVersion
import org.jetbrains.kotlin.idea.gradleTooling.KotlinMPPGradleModel
import org.jetbrains.kotlin.idea.gradleTooling.invokeWhenAtLeast
import org.jetbrains.kotlin.idea.projectModel.KotlinPlatform
import org.jetbrains.plugins.gradle.service.project.ProjectResolverContext

internal fun reportMultiplatformNotifications(mppModel: KotlinMPPGradleModel, resolverCtx: ProjectResolverContext) {
    if (!nativeDebugAdvertised && mppModel.kotlinNativeHome.isNotEmpty() && !SystemInfo.isWindows) {
        nativeDebugAdvertised = true
        suggestNativeDebug(resolverCtx.projectPath)
    }
    if (!kotlinJsInspectionPackAdvertised && mppModel.targets.any { it.platform == KotlinPlatform.JS }) {
        kotlinJsInspectionPackAdvertised = true
        suggestKotlinJsInspectionPackPlugin(resolverCtx.projectPath)
    }
    if (mppModel.targets.any { it.platform == KotlinPlatform.JS }) {
        val projectManager = ProjectManager.getInstance()
        val project = projectManager.openProjects.firstOrNull { it.basePath == resolverCtx.projectPath }
        if (project != null) {
            mppModel.kotlinGradlePluginVersion?.let { version ->
                showDeprecatedKotlinJsCompilerWarning(
                    project,
                    version,
                )
            }
        }
    }
    if (!resolverCtx.isResolveModulePerSourceSet && !KotlinPlatformUtils.isAndroidStudio && !PlatformUtils.isMobileIde() &&
        !PlatformUtils.isAppCode()
    ) {
        notifyLegacyIsResolveModulePerSourceSetSettingIfNeeded(resolverCtx.projectPath)
        resolverCtx.report(MessageEvent.Kind.WARNING, ResolveModulesPerSourceSetInMppBuildIssue())
    }
}

private fun showDeprecatedKotlinJsCompilerWarning(
    project: Project,
    kotlinGradlePluginVersion: KotlinGradlePluginVersion,
) {
    kotlinGradlePluginVersion.invokeWhenAtLeast("1.7.0") {
        if (
            !PropertiesComponent.getInstance(project).getBoolean(IGNORE_KOTLIN_JS_COMPILER_NOTIFICATION, false)
        ) {
            PropertiesComponent.getInstance(project).setValue(KOTLIN_JS_COMPILER_SHOULD_BE_NOTIFIED, true)
        }
    }
}

private var nativeDebugAdvertised = false
private var kotlinJsInspectionPackAdvertised = false
