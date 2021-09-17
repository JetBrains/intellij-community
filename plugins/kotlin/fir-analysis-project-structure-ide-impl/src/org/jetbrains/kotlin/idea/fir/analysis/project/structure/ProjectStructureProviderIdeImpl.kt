// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.fir.analysis.project.structure

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.util.containers.CollectionFactory
import org.jetbrains.kotlin.analysis.project.structure.KtModule
import org.jetbrains.kotlin.analysis.project.structure.ProjectStructureProvider
import org.jetbrains.kotlin.analyzer.ModuleInfo
import org.jetbrains.kotlin.idea.caches.project.*

internal class ProjectStructureProviderIdeImpl : ProjectStructureProvider() {
    private val cache = CollectionFactory.createConcurrentWeakIdentityMap<ModuleInfo, KtModule>()

    override fun getKtModuleForKtElement(element: PsiElement): KtModule {
        val moduleInfo = element.getModuleInfo(createSourceLibraryInfoForLibraryBinaries = false)
        return getKtModuleByModuleInfo(moduleInfo)
    }

    fun getKtModuleByModuleInfo(moduleInfo: ModuleInfo): KtModule =
        cache.getOrPut(moduleInfo) {
            createKtModuleByModuleInfo(moduleInfo)
        }

    private fun createKtModuleByModuleInfo(moduleInfo: ModuleInfo): KtModule = when (moduleInfo) {
        is ModuleSourceInfo -> KtSourceModuleByModuleInfo(moduleInfo, this)
        is LibraryInfo -> KtLibraryModuleByModuleInfo(moduleInfo, this)
        is SdkInfo -> SdkKtModuleByModuleInfo(moduleInfo, this)
        is LibrarySourceInfo -> KtLibrarySourceModuleByModuleInfo(moduleInfo, this)
        is NotUnderContentRootModuleInfo -> NotUnderContentRootModuleByModuleInfo(moduleInfo, this)
        else -> TODO("Unsupported module info ${moduleInfo::class} $moduleInfo")
    }

    companion object {
        fun getInstance(project: Project):ProjectStructureProviderIdeImpl {
            return project.getService(ProjectStructureProvider::class.java) as ProjectStructureProviderIdeImpl
        }
    }
}