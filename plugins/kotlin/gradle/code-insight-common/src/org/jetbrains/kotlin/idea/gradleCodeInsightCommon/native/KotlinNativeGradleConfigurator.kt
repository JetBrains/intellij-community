// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.gradleCodeInsightCommon.native

import com.intellij.openapi.module.Module
import org.jetbrains.kotlin.idea.base.projectStructure.ModuleSourceRootGroup
import org.jetbrains.kotlin.idea.base.projectStructure.toModuleGroup
import org.jetbrains.kotlin.idea.configuration.ConfigureKotlinStatus
import org.jetbrains.kotlin.idea.configuration.hasKotlinNativeRuntimeInScope
import org.jetbrains.kotlin.idea.gradleCodeInsightCommon.KotlinWithGradleConfigurator
import org.jetbrains.kotlin.platform.konan.NativePlatforms

open class KotlinNativeGradleConfigurator : KotlinWithGradleConfigurator() {
    override fun getKotlinPluginExpression(forKotlinDsl: Boolean): String = ""

    override fun getStatus(moduleSourceRootGroup: ModuleSourceRootGroup): ConfigureKotlinStatus {
        if (!isApplicable(moduleSourceRootGroup.baseModule))
            return ConfigureKotlinStatus.NON_APPLICABLE

        if (moduleSourceRootGroup.sourceRootModules.any(::hasKotlinNativeRuntimeInScope))
            return ConfigureKotlinStatus.CONFIGURED

        return ConfigureKotlinStatus.NON_APPLICABLE
    }
    override fun isApplicable(module: Module): Boolean {
        if (!super.isApplicable(module)) return false
        return module.toModuleGroup().sourceRootModules.any(::hasKotlinNativeRuntimeInScope)
    }

    override val kotlinPluginName: String get() = ""

    override val name: String get() = NAME

    override val targetPlatform get() = NativePlatforms.unspecifiedNativePlatform

    override val presentableText get() = PRESENTABLE_TEXT

    companion object {
        const val NAME = "KotlinNative"
        val PRESENTABLE_TEXT get() = KotlinGradleCodeInsightCommonBundle.message("native.gradle.name.short")
    }
}
