// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.projectStructure

import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.analyzer.LanguageSettingsProvider
import org.jetbrains.kotlin.analyzer.ModuleInfo
import org.jetbrains.kotlin.config.LanguageVersionSettings
import org.jetbrains.kotlin.idea.base.facet.platform.platform
import org.jetbrains.kotlin.idea.base.projectStructure.moduleInfo.LanguageSettingsOwner
import org.jetbrains.kotlin.idea.base.projectStructure.moduleInfo.LibraryInfo
import org.jetbrains.kotlin.idea.base.projectStructure.moduleInfo.ModuleSourceInfo
import org.jetbrains.kotlin.idea.base.projectStructure.moduleInfo.PlatformModuleInfo
import org.jetbrains.kotlin.idea.base.util.K1ModeProjectStructureApi
import org.jetbrains.kotlin.platform.TargetPlatformVersion
import org.jetbrains.kotlin.platform.jvm.JdkPlatform
import org.jetbrains.kotlin.platform.subplatformsOfType

@K1ModeProjectStructureApi
internal class IDELanguageSettingsProvider : LanguageSettingsProvider {
    override fun getLanguageVersionSettings(moduleInfo: ModuleInfo, project: Project): LanguageVersionSettings {
        return when (moduleInfo) {
            is ModuleSourceInfo -> moduleInfo.module.languageVersionSettings
            is LanguageSettingsOwner -> moduleInfo.languageVersionSettings
            is LibraryInfo -> LanguageVersionSettingsProvider.getInstance(project).librarySettings
            is PlatformModuleInfo -> moduleInfo.platformModule.module.languageVersionSettings
            else -> project.languageVersionSettings
        }
    }

    override fun getTargetPlatform(moduleInfo: ModuleInfo, project: Project): TargetPlatformVersion {
        return when (moduleInfo) {
            is ModuleSourceInfo -> {
                val jdkPlatform = moduleInfo.module.platform.subplatformsOfType<JdkPlatform>().firstOrNull()
                jdkPlatform?.targetVersion ?: TargetPlatformVersion.NoVersion
            }
            is LanguageSettingsOwner -> moduleInfo.targetPlatformVersion
            else -> TargetPlatformVersion.NoVersion
        }
    }
}