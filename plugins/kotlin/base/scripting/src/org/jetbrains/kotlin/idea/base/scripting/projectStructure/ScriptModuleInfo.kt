// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.scripting.projectStructure

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.kotlin.config.LanguageVersionSettings
import org.jetbrains.kotlin.idea.base.projectStructure.KotlinBaseProjectStructureBundle
import org.jetbrains.kotlin.idea.base.projectStructure.LibraryInfoCache
import org.jetbrains.kotlin.idea.base.projectStructure.moduleInfo.IdeaModuleInfo
import org.jetbrains.kotlin.idea.base.projectStructure.moduleInfo.LanguageSettingsOwner
import org.jetbrains.kotlin.idea.base.projectStructure.moduleInfo.ModuleOrigin
import org.jetbrains.kotlin.idea.base.projectStructure.moduleInfo.SdkInfo
import org.jetbrains.kotlin.idea.base.projectStructure.sourceModuleInfos
import org.jetbrains.kotlin.idea.base.scripting.getLanguageVersionSettings
import org.jetbrains.kotlin.idea.base.scripting.getPlatform
import org.jetbrains.kotlin.idea.base.scripting.getTargetPlatformVersion
import org.jetbrains.kotlin.idea.core.script.dependencies.KotlinScriptSearchScope
import org.jetbrains.kotlin.idea.core.script.dependencies.ScriptAdditionalIdeaDependenciesProvider
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.platform.TargetPlatform
import org.jetbrains.kotlin.platform.TargetPlatformVersion
import org.jetbrains.kotlin.resolve.PlatformDependentAnalyzerServices
import org.jetbrains.kotlin.resolve.jvm.platform.JvmPlatformAnalyzerServices
import org.jetbrains.kotlin.scripting.definitions.ScriptDefinition

data class ScriptModuleInfo(
    override val project: Project,
    val scriptFile: VirtualFile,
    val scriptDefinition: ScriptDefinition
) : IdeaModuleInfo, LanguageSettingsOwner {
    override val moduleOrigin: ModuleOrigin
        get() = ModuleOrigin.OTHER

    override val name: Name = Name.special("<script ${scriptFile.name} ${scriptDefinition.name}>")

    override val displayedName: String
        get() = KotlinBaseProjectStructureBundle.message("script.0.1", scriptFile.presentableName, scriptDefinition.name)

    override val contentScope
        get() = GlobalSearchScope.fileScope(project, scriptFile)

    override val moduleContentScope: GlobalSearchScope
        get() = KotlinScriptSearchScope(project, contentScope)

    override fun dependencies(): List<IdeaModuleInfo> = mutableSetOf<IdeaModuleInfo>(this).apply {
        val scriptDependentModules = ScriptAdditionalIdeaDependenciesProvider.getRelatedModules(scriptFile, project)
        scriptDependentModules.forEach {
            addAll(it.sourceModuleInfos)
        }

        val scriptDependentLibraries = ScriptAdditionalIdeaDependenciesProvider.getRelatedLibraries(scriptFile, project)
        val libraryInfoCache = LibraryInfoCache.getInstance(project)
        scriptDependentLibraries.forEach {
            addAll(libraryInfoCache[it])
        }

        val dependenciesInfo = ScriptDependenciesInfo.ForFile(project, scriptFile, scriptDefinition)
        add(dependenciesInfo)

        dependenciesInfo.sdk?.let { add(SdkInfo(project, it)) }
    }.toList()

    override val platform: TargetPlatform
        get() = getPlatform(project, scriptFile, scriptDefinition)

    override val analyzerServices: PlatformDependentAnalyzerServices
        get() = JvmPlatformAnalyzerServices

    override val languageVersionSettings: LanguageVersionSettings
        get() = getLanguageVersionSettings(project, scriptFile, scriptDefinition)

    override val targetPlatformVersion: TargetPlatformVersion
        get() = getTargetPlatformVersion(project, scriptFile, scriptDefinition)
}