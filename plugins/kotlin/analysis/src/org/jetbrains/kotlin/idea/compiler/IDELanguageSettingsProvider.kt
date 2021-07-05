// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.compiler

import com.intellij.openapi.components.service
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.roots.ProjectRootModificationTracker
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.util.CachedValue
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import org.jetbrains.kotlin.analyzer.LanguageSettingsProvider
import org.jetbrains.kotlin.analyzer.ModuleInfo
import org.jetbrains.kotlin.caches.project.cacheInvalidatingOnRootModifications
import org.jetbrains.kotlin.cli.common.arguments.JavaTypeEnhancementStateParser
import org.jetbrains.kotlin.cli.common.arguments.K2JVMCompilerArguments
import org.jetbrains.kotlin.cli.common.arguments.parseCommandLineArguments
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.config.*
import org.jetbrains.kotlin.idea.caches.project.*
import org.jetbrains.kotlin.idea.core.script.ScriptRelatedModuleNameFile
import org.jetbrains.kotlin.idea.project.*
import org.jetbrains.kotlin.platform.TargetPlatform
import org.jetbrains.kotlin.platform.TargetPlatformVersion
import org.jetbrains.kotlin.platform.jvm.JdkPlatform
import org.jetbrains.kotlin.platform.subplatformsOfType
import org.jetbrains.kotlin.scripting.definitions.ScriptDefinition
import org.jetbrains.kotlin.load.java.JavaTypeEnhancementState
import kotlin.KotlinVersion

class IDELanguageSettingsProviderHelper(private val project: Project) {
    internal val languageVersionSettings: LanguageVersionSettings
        get() = project.cacheInvalidatingOnRootModifications {
            project.getLanguageVersionSettings()
        }

    internal val languageVersionSettingsWithJavaTypeEnhancementState: LanguageVersionSettings
        get() = project.cacheInvalidatingOnRootModifications {
            project.getLanguageVersionSettings(
                javaTypeEnhancementState = computeJavaTypeEnhancementState(project)
            )
        }

    private fun computeJavaTypeEnhancementState(project: Project): JavaTypeEnhancementState? {
        var result: JavaTypeEnhancementState? = null
        for (module in ModuleManager.getInstance(project).modules) {
            val settings = KotlinFacetSettingsProvider.getInstance(project)?.getSettings(module) ?: continue
            val compilerArguments = settings.mergedCompilerArguments as? K2JVMCompilerArguments ?: continue
            val kotlinVersion =
                LanguageVersion.fromVersionString(compilerArguments.languageVersion)?.toKotlinVersion() ?: KotlinVersion.CURRENT

            result = JavaTypeEnhancementStateParser(MessageCollector.NONE, kotlinVersion).parse(
                compilerArguments.jsr305,
                compilerArguments.supportCompatqualCheckerFrameworkAnnotations,
                compilerArguments.jspecifyAnnotations,
                compilerArguments.nullabilityAnnotations
            )

        }
        return result
    }

    companion object {
        fun getInstance(project: Project): IDELanguageSettingsProviderHelper = project.service()
    }
}

object IDELanguageSettingsProvider : LanguageSettingsProvider {
    override fun getLanguageVersionSettings(
        moduleInfo: ModuleInfo,
        project: Project
    ): LanguageVersionSettings =
        when (moduleInfo) {
            is ModuleSourceInfo -> moduleInfo.module.languageVersionSettings
            is LibraryInfo -> IDELanguageSettingsProviderHelper.getInstance(project).languageVersionSettingsWithJavaTypeEnhancementState
            is ScriptModuleInfo -> {
                getLanguageSettingsForScripts(
                    project,
                    moduleInfo.scriptFile,
                    moduleInfo.scriptDefinition
                ).languageVersionSettings
            }

            is ScriptDependenciesInfo.ForFile ->
                getLanguageSettingsForScripts(
                    project,
                    moduleInfo.scriptFile,
                    moduleInfo.scriptDefinition
                ).languageVersionSettings
            is PlatformModuleInfo -> moduleInfo.platformModule.module.languageVersionSettings
            else -> IDELanguageSettingsProviderHelper.getInstance(project).languageVersionSettings
        }

    // TODO(dsavvinov): get rid of this method; instead store proper instance of TargetPlatformVersion in platform-instance
    override fun getTargetPlatform(moduleInfo: ModuleInfo, project: Project): TargetPlatformVersion =
        when (moduleInfo) {
            is ModuleSourceInfo ->
                moduleInfo.module.platform?.subplatformsOfType<JdkPlatform>()?.firstOrNull()?.targetVersion
                    ?: TargetPlatformVersion.NoVersion
            is ScriptModuleInfo,
            is ScriptDependenciesInfo.ForFile -> detectDefaultTargetPlatformVersion(moduleInfo.platform)
            else -> TargetPlatformVersion.NoVersion
        }
}

private data class ScriptLanguageSettings(
    val languageVersionSettings: LanguageVersionSettings,
    val targetPlatformVersion: TargetPlatformVersion
)

private val SCRIPT_LANGUAGE_SETTINGS = Key.create<CachedValue<ScriptLanguageSettings>>("SCRIPT_LANGUAGE_SETTINGS")

fun getTargetPlatformVersionForScript(project: Project, file: VirtualFile, scriptDefinition: ScriptDefinition): TargetPlatformVersion {
    return getLanguageSettingsForScripts(project, file, scriptDefinition).targetPlatformVersion
}

private fun detectDefaultTargetPlatformVersion(platform: TargetPlatform?): TargetPlatformVersion {
    return platform?.subplatformsOfType<JdkPlatform>()?.firstOrNull()?.targetVersion ?: TargetPlatformVersion.NoVersion
}

private fun getLanguageSettingsForScripts(project: Project, file: VirtualFile, scriptDefinition: ScriptDefinition): ScriptLanguageSettings {
    val scriptModule = file.let {
        ScriptRelatedModuleNameFile[project, it]?.let { module -> ModuleManager.getInstance(project).findModuleByName(module) }
            ?: ProjectFileIndex.SERVICE.getInstance(project).getModuleForFile(it)
    }

    val environmentCompilerOptions = scriptDefinition.defaultCompilerOptions
    val args = scriptDefinition.compilerOptions
    return if (environmentCompilerOptions.none() && args.none()) {
        ScriptLanguageSettings(
            project.getLanguageVersionSettings(contextModule = scriptModule),
            detectDefaultTargetPlatformVersion(scriptModule?.platform)
        )
    } else {
        val settings = scriptDefinition.getUserData(SCRIPT_LANGUAGE_SETTINGS) ?: createCachedValue(project) {
            val compilerArguments = K2JVMCompilerArguments()
            parseCommandLineArguments(environmentCompilerOptions.toList(), compilerArguments)
            parseCommandLineArguments(args.toList(), compilerArguments)
            // TODO: reporting
            val versionSettings = compilerArguments.toLanguageVersionSettings(MessageCollector.NONE)
            val jvmTarget =
                compilerArguments.jvmTarget?.let { JvmTarget.fromString(it) } ?: detectDefaultTargetPlatformVersion(scriptModule?.platform)
            ScriptLanguageSettings(versionSettings, jvmTarget)
        }.also { scriptDefinition.putUserData(SCRIPT_LANGUAGE_SETTINGS, it) }
        settings.value
    }
}

private inline fun createCachedValue(
    project: Project,
    crossinline body: () -> ScriptLanguageSettings
): CachedValue<ScriptLanguageSettings> {
    return CachedValuesManager
        .getManager(project)
        .createCachedValue(
            {
                CachedValueProvider.Result(
                    body(),
                    ProjectRootModificationTracker.getInstance(project), ModuleManager.getInstance(project)
                )
            }, false
        )
}
