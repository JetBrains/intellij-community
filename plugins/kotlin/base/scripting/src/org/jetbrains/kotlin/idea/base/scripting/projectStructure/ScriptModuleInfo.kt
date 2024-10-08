// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.scripting.projectStructure

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.backend.workspace.WorkspaceModel
import com.intellij.platform.backend.workspace.toVirtualFileUrl
import com.intellij.platform.workspace.jps.entities.LibraryDependency
import com.intellij.platform.workspace.jps.entities.ModuleEntity
import com.intellij.platform.workspace.storage.EntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.workspaceModel.ide.impl.legacyBridge.library.ProjectLibraryTableBridgeImpl.Companion.libraryMap
import com.intellij.workspaceModel.ide.impl.legacyBridge.module.findModule
import com.intellij.workspaceModel.ide.legacyBridge.ModuleBridge
import org.jetbrains.kotlin.config.LanguageVersionSettings
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginModeProvider
import org.jetbrains.kotlin.idea.base.projectStructure.KotlinBaseProjectStructureBundle
import org.jetbrains.kotlin.idea.base.projectStructure.KotlinResolveScopeEnlarger
import org.jetbrains.kotlin.idea.base.projectStructure.LibraryInfoCache
import org.jetbrains.kotlin.idea.base.projectStructure.moduleInfo.*
import org.jetbrains.kotlin.idea.base.projectStructure.sourceModuleInfos
import org.jetbrains.kotlin.idea.base.scripting.getLanguageVersionSettings
import org.jetbrains.kotlin.idea.base.scripting.getPlatform
import org.jetbrains.kotlin.idea.base.scripting.getTargetPlatformVersion
import org.jetbrains.kotlin.idea.core.script.ScriptDependencyAware
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

    override val name: Name
        get() = Name.special("<script ${scriptFile.name} ${scriptDefinition.name}>")

    override val displayedName: String
        get() = KotlinBaseProjectStructureBundle.message("script.0.1", scriptFile.presentableName, scriptDefinition.name)

    private val _contentScope: GlobalSearchScope by lazy {
        val basicScriptScope = GlobalSearchScope.fileScope(project, scriptFile)

        if (KotlinPluginModeProvider.isK1Mode()) {
            basicScriptScope
        } else {
            val snapshot = WorkspaceModel.getInstance(project).currentSnapshot

            scriptFile.workspaceEntities(project, snapshot).filterIsInstance<ModuleEntity>().firstOrNull()
                ?.let<ModuleEntity, GlobalSearchScope?> {
                    it.findModule(snapshot)?.let<ModuleBridge, GlobalSearchScope> { module ->
                        val scope = KotlinResolveScopeEnlarger.enlargeScope(
                            module.getModuleWithDependenciesAndLibrariesScope(false),
                            module,
                            isTestScope = false
                        )
                        basicScriptScope.union(scope)
                    }
                } ?: basicScriptScope
        }
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

            if (KotlinPluginModeProvider.isK1Mode()) {
                val dependenciesInfo = ScriptDependenciesInfo.ForFile(project, scriptFile, scriptDefinition)
                add(dependenciesInfo)
            } else {
                scriptFile.scriptLibraryDependencies(project).forEach(::add)
            }

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

internal fun VirtualFile.workspaceEntities(project: Project, snapshot: EntityStorage): Sequence<WorkspaceEntity> {
    val virtualFileUrlManager = WorkspaceModel.getInstance(project).getVirtualFileUrlManager()
    val virtualFileUrl = toVirtualFileUrl(virtualFileUrlManager)
    return snapshot.getVirtualFileUrlIndex().findEntitiesByUrl(virtualFileUrl)
}

internal fun VirtualFile.scriptModuleEntity(project: Project, snapshot: EntityStorage): ModuleEntity? {
    val virtualFileUrlManager = WorkspaceModel.getInstance(project).getVirtualFileUrlManager()
    val virtualFileUrl = toVirtualFileUrl(virtualFileUrlManager)
    return snapshot.getVirtualFileUrlIndex().findEntitiesByUrl(virtualFileUrl).firstNotNullOfOrNull { it as? ModuleEntity }
}

fun VirtualFile.scriptLibraryDependencies(project: Project): Sequence<LibraryInfo> {
    val storage = WorkspaceModel.getInstance(project).currentSnapshot
    val cache = LibraryInfoCache.getInstance(project)

    val dependencies = scriptModuleEntity(project, storage)?.dependencies ?: emptyList()
    return dependencies.asSequence()
        .mapNotNull {
            (it as? LibraryDependency)?.library?.resolve(storage)
        }.mapNotNull {
            storage.libraryMap.getDataByEntity(it)
        }.flatMap {
            cache[it]
        }
}