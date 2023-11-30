// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.scripting.projectStructure

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.kotlin.analysis.project.structure.*
import org.jetbrains.kotlin.analyzer.ModuleInfo
import org.jetbrains.kotlin.config.LanguageVersionSettings
import org.jetbrains.kotlin.idea.base.projectStructure.KtModuleByModuleInfoBase
import org.jetbrains.kotlin.idea.base.projectStructure.KtModuleFactory
import org.jetbrains.kotlin.idea.base.projectStructure.toKtModuleOfType
import org.jetbrains.kotlin.idea.core.script.ScriptConfigurationManager
import org.jetbrains.kotlin.psi.KtFile
import java.nio.file.Path

internal class ScriptingKtModuleFactory : KtModuleFactory {
    override fun createModule(moduleInfo: ModuleInfo): KtModule? {
        return when (moduleInfo) {
            is ScriptModuleInfo -> KtScriptModuleByModuleInfo(moduleInfo)
            is ScriptDependenciesInfo -> KtScriptDependencyModuleByModuleInfo(moduleInfo)
            is ScriptDependenciesSourceInfo -> KtScriptDependencySourceModuleByModuleInfo(moduleInfo)
            else -> null
        }
    }
}

private class KtScriptModuleByModuleInfo(
    private val moduleInfo: ScriptModuleInfo
) : KtModuleByModuleInfoBase(moduleInfo), KtScriptModule {
    override val project: Project
        get() = moduleInfo.project

    override val file: KtFile
        get() = getScriptFile(moduleInfo.scriptFile)

    override val contentScope: GlobalSearchScope
        get() = moduleInfo.moduleContentScope

    override val languageVersionSettings: LanguageVersionSettings
        get() = moduleInfo.languageVersionSettings

    override fun hashCode(): Int {
        return moduleInfo.hashCode()
    }

    override fun equals(other: Any?): Boolean = this === other || other is KtScriptModuleByModuleInfo && moduleInfo == other.moduleInfo
}

private class KtScriptDependencyModuleByModuleInfo(
    private val moduleInfo: ScriptDependenciesInfo
) : KtModuleByModuleInfoBase(moduleInfo), KtLibraryModule, KtScriptDependencyModule {
    override val project: Project
        get() = moduleInfo.project

    override val contentScope: GlobalSearchScope
        get() = moduleInfo.contentScope

    override val libraryName: String
        get() = "Script dependencies"

    override val librarySources: KtLibrarySourceModule?
        get() = moduleInfo.sourcesModuleInfo?.toKtModuleOfType<KtLibrarySourceModule>()

    override fun getBinaryRoots(): Collection<Path> {
        when (moduleInfo) {
            is ScriptDependenciesInfo.ForProject -> {
                return ScriptConfigurationManager.getInstance(project)
                    .getAllScriptsDependenciesClassFiles()
                    .map { it.toNioPath() }
            }
            is ScriptDependenciesInfo.ForFile -> {
                return ScriptConfigurationManager.getInstance(project)
                    .getScriptDependenciesClassFiles(moduleInfo.scriptFile)
                    .map { it.toNioPath() }
            }
        }
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
) : KtModuleByModuleInfoBase(moduleInfo), KtLibrarySourceModule, KtScriptDependencyModule {
    override val project: Project
        get() = moduleInfo.project

    override val contentScope: GlobalSearchScope
        get() = moduleInfo.contentScope

    override val libraryName: String
        get() = "Script dependency sources"

    override val binaryLibrary: KtLibraryModule
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

private fun KtModuleByModuleInfoBase.getScriptFile(virtualFile: VirtualFile): KtFile {
    val project = ideaModuleInfo.project
    return PsiManager.getInstance(project).findFile(virtualFile) as? KtFile
        ?: error("Cannot find a KtFile for a script '$virtualFile'")
}