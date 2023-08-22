// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.scripting

import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.util.CachedValue
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import org.jetbrains.kotlin.cli.common.arguments.K2JVMCompilerArguments
import org.jetbrains.kotlin.cli.common.arguments.parseCommandLineArguments
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.config.AnalysisFlags
import org.jetbrains.kotlin.config.JvmTarget
import org.jetbrains.kotlin.config.LanguageVersionSettings
import org.jetbrains.kotlin.idea.base.facet.platform.TargetPlatformDetector
import org.jetbrains.kotlin.idea.base.facet.platform.platform
import org.jetbrains.kotlin.idea.base.projectStructure.languageVersionSettings
import org.jetbrains.kotlin.idea.compiler.configuration.KotlinCompilerSettingsTracker
import org.jetbrains.kotlin.idea.core.script.ScriptRelatedModuleNameFile
import org.jetbrains.kotlin.idea.core.script.scriptingDebugLog
import org.jetbrains.kotlin.idea.facet.KotlinFacetModificationTracker
import org.jetbrains.kotlin.platform.CommonPlatforms
import org.jetbrains.kotlin.platform.TargetPlatform
import org.jetbrains.kotlin.platform.TargetPlatformVersion
import org.jetbrains.kotlin.platform.jvm.JdkPlatform
import org.jetbrains.kotlin.platform.jvm.JvmPlatforms.defaultJvmPlatform
import org.jetbrains.kotlin.platform.subplatformsOfType
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.scripting.definitions.ScriptDefinition
import org.jetbrains.kotlin.scripting.definitions.findScriptDefinition

private val SCRIPT_LANGUAGE_SETTINGS = Key.create<CachedValue<ScriptLanguageSettings>>("SCRIPT_LANGUAGE_SETTINGS")

private data class ScriptLanguageSettings(
    val languageVersionSettings: LanguageVersionSettings,
    val targetPlatformVersion: TargetPlatformVersion
)

private val gradleTemplatesClasses = setOf(
  "org.gradle.kotlin.dsl.KotlinBuildScript",
  "org.gradle.kotlin.dsl.KotlinSettingsScript",
  "org.gradle.kotlin.dsl.KotlinInitScript"
)

internal class ScriptingTargetPlatformDetector : TargetPlatformDetector {
    override fun detectPlatform(file: KtFile): TargetPlatform? {
        val definition = runReadAction {
            file.takeIf { it.isValid && it.isScript() }?.findScriptDefinition()
        } ?: return null

        val virtualFile = file.originalFile.virtualFile ?: return null
        return getPlatform(file.project, virtualFile, definition)
    }
}

internal fun getLanguageVersionSettings(project: Project, virtualFile: VirtualFile, definition: ScriptDefinition): LanguageVersionSettings {
    return getScriptSettings(project, virtualFile, definition).languageVersionSettings
}

internal fun getTargetPlatformVersion(project: Project, virtualFile: VirtualFile, definition: ScriptDefinition): TargetPlatformVersion {
    return getScriptSettings(project, virtualFile, definition).targetPlatformVersion
}

internal fun getPlatform(project: Project, virtualFile: VirtualFile, definition: ScriptDefinition): TargetPlatform {
    val targetPlatformVersion = getScriptSettings(project, virtualFile, definition).targetPlatformVersion
    if (targetPlatformVersion != TargetPlatformVersion.NoVersion) {
        for (compilerPlatform in CommonPlatforms.allSimplePlatforms) {
            if (compilerPlatform.single().targetPlatformVersion == targetPlatformVersion) {
                return compilerPlatform
            }
        }
    }

    val platformNameFromDefinition = definition.platform

    if ("JVM" == platformNameFromDefinition) { // optional compiler arg "-jvmTarget" is considered in getScriptSettings()
        return defaultJvmPlatform
    }

    for (compilerPlatform in CommonPlatforms.allSimplePlatforms) {
        // FIXME(dsavvinov): get rid of matching by name
        if (compilerPlatform.single().platformName == platformNameFromDefinition) {
            return compilerPlatform
        }
    }

    return defaultJvmPlatform
}

private inline fun createCachedValue(project: Project, crossinline body: () -> ScriptLanguageSettings): CachedValue<ScriptLanguageSettings> {
    return CachedValuesManager.getManager(project).createCachedValue {
        CachedValueProvider.Result(
            body(),
            KotlinFacetModificationTracker.getInstance(project),
            KotlinCompilerSettingsTracker.getInstance(project)
        )
    }
}

private fun getScriptSettings(project: Project, virtualFile: VirtualFile, definition: ScriptDefinition): ScriptLanguageSettings {
    val compilerOptions = definition.defaultCompilerOptions + definition.compilerOptions.addGradleSpecificsIfNeeded(definition)

    return if (compilerOptions.isEmpty()) {
        val scriptModule = getScriptModule(project, virtualFile)
        val languageVersionSettings = scriptModule?.languageVersionSettings ?: project.languageVersionSettings
        val platformVersion = detectDefaultTargetPlatformVersion(scriptModule?.platform)
        ScriptLanguageSettings(languageVersionSettings, platformVersion)
    } else {
        val settings = definition.getUserData(SCRIPT_LANGUAGE_SETTINGS) ?: createCachedValue(project) {
            val compilerArguments = K2JVMCompilerArguments()

            parseCommandLineArguments(compilerOptions, compilerArguments)
            scriptingDebugLog(virtualFile) { "compiler options: $compilerOptions" }

            val languageVersionSettings = compilerArguments.toLanguageVersionSettings(
              MessageCollector.NONE,
              mapOf(AnalysisFlags.ideMode to true)
            )
            val scriptModule = getScriptModule(project, virtualFile)?.takeIf { !it.isDisposed }
            val platformVersion = compilerArguments.jvmTarget?.let { JvmTarget.fromString(it) }
                                  ?: detectDefaultTargetPlatformVersion(scriptModule?.platform)

            ScriptLanguageSettings(languageVersionSettings, platformVersion)
        }.also { definition.putUserData(SCRIPT_LANGUAGE_SETTINGS, it) }
        settings.value
    }
}

private fun Iterable<String>.addGradleSpecificsIfNeeded(definition: ScriptDefinition): Iterable<String> {
    if (!none() || (definition.baseClassType.typeName !in gradleTemplatesClasses)) return this

    return listOf(
            "-java-parameters",
            "-Xjvm-default=all",
            "-Xjsr305=strict",
            "-Xsam-conversions=class",
            "-XXLanguage:+DisableCompatibilityModeForNewInference",
            "-XXLanguage:-TypeEnhancementImprovementsInStrictMode")
}

private fun getScriptModule(project: Project, virtualFile: VirtualFile): Module? {
    val scriptModuleName = ScriptRelatedModuleNameFile[project, virtualFile]
    return if (scriptModuleName != null) {
        ModuleManager.getInstance(project).findModuleByName(scriptModuleName)
    } else {
        ProjectFileIndex.getInstance(project).getModuleForFile(virtualFile)
    }
}

private fun detectDefaultTargetPlatformVersion(platform: TargetPlatform?): TargetPlatformVersion {
    return platform?.subplatformsOfType<JdkPlatform>()?.firstOrNull()?.targetVersion ?: TargetPlatformVersion.NoVersion
}