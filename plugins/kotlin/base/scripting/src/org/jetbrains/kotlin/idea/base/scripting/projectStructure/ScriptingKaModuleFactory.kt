// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:OptIn(K1ModeProjectStructureApi::class)

package org.jetbrains.kotlin.idea.base.scripting.projectStructure

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.platform.projectStructure.KaContentScopeProvider
import org.jetbrains.kotlin.analysis.api.projectStructure.*
import org.jetbrains.kotlin.analyzer.ModuleInfo
import org.jetbrains.kotlin.config.LanguageVersionSettings
import org.jetbrains.kotlin.idea.base.projectStructure.*
import org.jetbrains.kotlin.idea.base.projectStructure.moduleInfo.JvmLibraryInfo
import org.jetbrains.kotlin.idea.base.projectStructure.moduleInfo.LibrarySourceInfo
import org.jetbrains.kotlin.idea.base.util.K1ModeProjectStructureApi
import org.jetbrains.kotlin.idea.core.script.KotlinScriptEntitySource
import org.jetbrains.kotlin.idea.core.script.ScriptDependencyAware
import org.jetbrains.kotlin.idea.core.script.dependencies.ScriptAdditionalIdeaDependenciesProvider
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.utils.addToStdlib.safeAs
import java.nio.file.Path

internal class ScriptingKaModuleFactory : KaModuleFactory {
    override fun createModule(moduleInfo: ModuleInfo): KaModule? {
        return when (moduleInfo) {
            is ScriptModuleInfo -> KtScriptModuleByModuleInfo(moduleInfo)
            is ScriptDependenciesInfo -> KtScriptDependencyModuleByModuleInfo(moduleInfo)
            is ScriptDependenciesSourceInfo -> KtScriptDependencySourceModuleByModuleInfo(moduleInfo)
            is JvmLibraryInfo -> moduleInfo.source?.safeAs<KotlinScriptEntitySource>()?.let {
                KtScriptLibraryModuleByModuleInfo(moduleInfo)
            }
            is LibrarySourceInfo -> moduleInfo.source?.safeAs<KotlinScriptEntitySource>()?.let {
                KtScriptLibrarySourceModuleByModuleInfo(moduleInfo)
            }
            else -> null
        }
    }

}

@OptIn(KaExperimentalApi::class)
private class KtScriptModuleByModuleInfo(
    private val moduleInfo: ScriptModuleInfo
) : KtModuleByModuleInfoBase(moduleInfo), KaScriptModule {
    private var hasDirectFriendDependencies: Boolean? = null

    override val project: Project
        get() = moduleInfo.project

    override val file: KtFile
        get() = getScriptFile(moduleInfo.scriptFile)

    override val baseContentScope: GlobalSearchScope
        get() = moduleInfo.moduleContentScope

    override val languageVersionSettings: LanguageVersionSettings
        get() = moduleInfo.languageVersionSettings

    override fun computeDirectFriendDependencies(): List<KaModule> =
        if (hasDirectFriendDependencies == false) {
            emptyList()
        } else {
            val ktModules = ScriptAdditionalIdeaDependenciesProvider.getRelatedModules(moduleInfo.scriptFile, moduleInfo.project)
                .mapNotNull { it.toKaSourceModuleForProduction() }
            hasDirectFriendDependencies = ktModules.isNotEmpty()
            ktModules
        }

    override fun hashCode(): Int {
        return moduleInfo.hashCode()
    }

    override fun equals(other: Any?): Boolean = this === other || other is KtScriptModuleByModuleInfo && moduleInfo == other.moduleInfo
}

private class KtScriptDependencyModuleByModuleInfo(
    private val moduleInfo: ScriptDependenciesInfo
) : KtModuleByModuleInfoBase(moduleInfo), KaLibraryModule, KaScriptDependencyModule {
    override val project: Project
        get() = moduleInfo.project

    override val baseContentScope: GlobalSearchScope
        get() = moduleInfo.contentScope

    override val libraryName: String
        get() = "Script dependencies"

    @OptIn(K1ModeProjectStructureApi::class)
    override val librarySources: KaLibrarySourceModule?
        get() = moduleInfo.sourcesModuleInfo?.toKaModuleOfType<KaLibrarySourceModule>()

    override val isSdk: Boolean
        get() = false

    @OptIn(KaExperimentalApi::class)
    override val binaryRoots: Collection<Path>
        get() = binaryVirtualFiles.map { it.toNioPath() }

    @KaExperimentalApi
    override val binaryVirtualFiles: Collection<VirtualFile> =
        when (moduleInfo) {
            is ScriptDependenciesInfo.ForProject -> ScriptDependencyAware.getInstance(project).getAllScriptsDependenciesClassFiles()

            is ScriptDependenciesInfo.ForFile -> ScriptDependencyAware.getInstance(project)
                .getScriptDependenciesClassFiles(moduleInfo.scriptFile)
        }

    override val file: KtFile?
        get() = optScriptFile((moduleInfo as? ScriptDependenciesInfo.ForFile)?.scriptFile)

    override fun hashCode(): Int {
        return moduleInfo.hashCode()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true

        if (other !is KtScriptDependencyModuleByModuleInfo || moduleInfo != other.moduleInfo) {
            return false
        }

        // 'equals()' for 'ScriptDependenciesInfo.ForFile' doesn't include the script file
        if (moduleInfo is ScriptDependenciesInfo.ForFile) {
            return other.moduleInfo is ScriptDependenciesInfo.ForFile
                    && moduleInfo.scriptFile == other.moduleInfo.scriptFile
        }

        return true
    }
}

private class KtScriptDependencySourceModuleByModuleInfo(
    private val moduleInfo: ScriptDependenciesSourceInfo
) : KtModuleByModuleInfoBase(moduleInfo), KaLibrarySourceModule, KaScriptDependencyModule {
    override val project: Project
        get() = moduleInfo.project

    override val baseContentScope: GlobalSearchScope
        get() = moduleInfo.sourceScope()

    override val libraryName: String
        get() = "Script dependency sources"

    override val binaryLibrary: KaLibraryModule
        get() = KtScriptDependencyModuleByModuleInfo(moduleInfo.binariesModuleInfo)

    override val file: KtFile?
        get() = optScriptFile((moduleInfo.binariesModuleInfo as? ScriptDependenciesInfo.ForFile)?.scriptFile)

    override fun hashCode(): Int {
        return moduleInfo.hashCode()
    }

    override fun equals(other: Any?): Boolean {
        return this === other || other is KtScriptDependencySourceModuleByModuleInfo && moduleInfo == other.moduleInfo
    }
}

private fun KtModuleByModuleInfoBase.optScriptFile(virtualFile: VirtualFile?): KtFile? {
    if (virtualFile == null) {
        return null
    }

    return getScriptFile(virtualFile)
}

@OptIn(K1ModeProjectStructureApi::class)
private fun KtModuleByModuleInfoBase.getScriptFile(virtualFile: VirtualFile): KtFile {
    val project = ideaModuleInfo.project
    return PsiManager.getInstance(project).findFile(virtualFile) as? KtFile
        ?: error("Cannot find a KtFile for a script '$virtualFile'")
}