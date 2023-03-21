// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.gradleJava.configuration.mpp

import com.intellij.build.events.MessageEvent
import com.intellij.openapi.util.SystemInfo
import com.intellij.util.PlatformUtils
import org.jetbrains.kotlin.idea.base.util.KotlinPlatformUtils
import org.jetbrains.kotlin.idea.gradle.configuration.ResolveModulesPerSourceSetInMppBuildIssue
import org.jetbrains.kotlin.idea.gradle.configuration.suggestNativeDebug
import org.jetbrains.kotlin.idea.gradle.ui.notifyLegacyIsResolveModulePerSourceSetSettingIfNeeded
import org.jetbrains.kotlin.idea.gradleTooling.KotlinMPPGradleModel
import org.jetbrains.plugins.gradle.service.project.ProjectResolverContext

internal fun reportMultiplatformNotifications(mppModel: KotlinMPPGradleModel, resolverCtx: ProjectResolverContext) {
    if (!nativeDebugAdvertised && mppModel.kotlinNativeHome.isNotEmpty() && !SystemInfo.isWindows) {
        nativeDebugAdvertised = true
        suggestNativeDebug(resolverCtx.projectPath)
    }
    if (!resolverCtx.isResolveModulePerSourceSet && !KotlinPlatformUtils.isAndroidStudio && !PlatformUtils.isMobileIde() &&
        !PlatformUtils.isAppCode()
    ) {
        notifyLegacyIsResolveModulePerSourceSetSettingIfNeeded(resolverCtx.projectPath)
        resolverCtx.report(MessageEvent.Kind.WARNING, ResolveModulesPerSourceSetInMppBuildIssue())
    }
}

private var nativeDebugAdvertised = false
