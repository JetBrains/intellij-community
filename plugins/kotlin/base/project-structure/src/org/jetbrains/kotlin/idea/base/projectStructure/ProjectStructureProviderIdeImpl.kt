// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.base.projectStructure

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.analysis.project.structure.KtModule
import org.jetbrains.kotlin.analysis.project.structure.ProjectStructureProvider
import org.jetbrains.kotlin.analyzer.ModuleInfo
import org.jetbrains.kotlin.idea.base.projectStructure.moduleInfo.*
import org.jetbrains.kotlin.psi.KtFile

internal class ProjectStructureProviderIdeImpl(private val project: Project) : ProjectStructureProvider() {
    override fun getKtModuleForKtElement(element: PsiElement): KtModule {
        val config = ModuleInfoProvider.Configuration(createSourceLibraryInfoForLibraryBinaries = false)
        val moduleInfo = ModuleInfoProvider.getInstance(element.project).firstOrNull(element, config)
            ?: NotUnderContentRootModuleInfo(project, element.containingFile as? KtFile)

        return getKtModuleByModuleInfo(moduleInfo)
    }

    // TODO maybe introduce some cache?
    fun getKtModuleByModuleInfo(moduleInfo: ModuleInfo): KtModule =
        createKtModuleByModuleInfo(moduleInfo)

    private fun createKtModuleByModuleInfo(moduleInfo: ModuleInfo): KtModule = when (moduleInfo) {
        is ModuleSourceInfo -> KtSourceModuleByModuleInfo(moduleInfo, this)
        is LibraryInfo -> KtLibraryModuleByModuleInfo(moduleInfo, this)
        is SdkInfo -> SdkKtModuleByModuleInfo(moduleInfo, this)
        is LibrarySourceInfo -> KtLibrarySourceModuleByModuleInfo(moduleInfo, this)
        is NotUnderContentRootModuleInfo -> NotUnderContentRootModuleByModuleInfo(moduleInfo, this)
        else -> NotUnderContentRootModuleByModuleInfo(moduleInfo as IdeaModuleInfo, this)
    }

    companion object {
        fun getInstance(project: Project): ProjectStructureProviderIdeImpl {
            return project.getService(ProjectStructureProvider::class.java) as ProjectStructureProviderIdeImpl
        }
    }
}
