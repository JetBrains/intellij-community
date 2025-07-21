// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.core.script.v1

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
import org.jetbrains.kotlin.idea.base.util.K1ModeProjectStructureApi
import org.jetbrains.kotlin.idea.core.script.v1.ScriptAdditionalIdeaDependenciesProvider
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.platform.TargetPlatform
import org.jetbrains.kotlin.platform.TargetPlatformVersion
import org.jetbrains.kotlin.resolve.PlatformDependentAnalyzerServices
import org.jetbrains.kotlin.resolve.jvm.platform.JvmPlatformAnalyzerServices
import org.jetbrains.kotlin.scripting.definitions.ScriptDefinition

@OptIn(K1ModeProjectStructureApi::class)
data class ScriptModuleInfo(
  override val project: Project,
  val scriptFile: VirtualFile,
  val scriptDefinition: ScriptDefinition
) : IdeaModuleInfo, LanguageSettingsOwner {
    override val moduleOrigin: ModuleOrigin
        get() = ModuleOrigin.OTHER

    override val name: Name
        get() = Name.special("<script ${scriptFile.name} ${scriptDefinition.name}>")

    override val displayedName: String
        get() = KotlinBaseProjectStructureBundle.message("script.0.1", scriptFile.presentableName, scriptDefinition.name)

    private val _contentScope: GlobalSearchScope by lazy {
        GlobalSearchScope.fileScope(project, scriptFile)
    }

    override val contentScope: GlobalSearchScope
        get() = _contentScope

    override val moduleContentScope: GlobalSearchScope
        get() = KotlinScriptSearchScope(project, contentScope)

    private val _dependencies: List<IdeaModuleInfo> by lazy {
        mutableSetOf<IdeaModuleInfo>(this).apply {
            val scriptDependentLibraries = ScriptAdditionalIdeaDependenciesProvider.getRelatedLibraries(scriptFile, project)
            val libraryInfoCache = LibraryInfoCache.getInstance(project)
            scriptDependentLibraries.forEach {
                addAll(libraryInfoCache[it])
            }

            val scriptDependentModules = ScriptAdditionalIdeaDependenciesProvider.getRelatedModules(scriptFile, project)
            scriptDependentModules.forEach {
                addAll(it.sourceModuleInfos)
            }

            add(ScriptDependenciesInfo.ForFile(project, scriptFile, scriptDefinition))

            val sdk = ScriptDependencyAware.getInstance(project).getScriptSdk(scriptFile)
            sdk?.let { add(SdkInfo(project, it)) }
        }.toList()
    }

    override fun dependencies(): List<IdeaModuleInfo> = _dependencies

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ScriptModuleInfo) return false

        if (project != other.project) return false
        if (scriptFile != other.scriptFile) return false

        return true
    }

    override fun hashCode(): Int {
        var result = project.hashCode()
        result = 31 * result + scriptFile.hashCode()
        return result
    }

    override val platform: TargetPlatform
        get() = getPlatform(project, scriptFile, scriptDefinition)

    override val analyzerServices: PlatformDependentAnalyzerServices
        get() = JvmPlatformAnalyzerServices

    override val languageVersionSettings: LanguageVersionSettings
        get() = getLanguageVersionSettings(project, scriptFile, scriptDefinition)

    override val targetPlatformVersion: TargetPlatformVersion
        get() = getTargetPlatformVersion(project, scriptFile, scriptDefinition)
}