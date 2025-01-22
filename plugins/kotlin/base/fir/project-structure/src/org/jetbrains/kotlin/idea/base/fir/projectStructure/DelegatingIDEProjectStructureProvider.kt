// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.fir.projectStructure

import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.workspace.jps.entities.LibraryEntity
import com.intellij.platform.workspace.jps.entities.LibraryId
import com.intellij.platform.workspace.jps.entities.ModuleEntity
import com.intellij.platform.workspace.jps.entities.ModuleId
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import org.jetbrains.kotlin.analysis.api.projectStructure.KaLibraryModule
import org.jetbrains.kotlin.analysis.api.projectStructure.KaModule
import org.jetbrains.kotlin.analysis.api.projectStructure.KaNotUnderContentRootModule
import org.jetbrains.kotlin.analysis.api.projectStructure.KaSourceModule
import org.jetbrains.kotlin.config.LanguageVersionSettings
import org.jetbrains.kotlin.idea.base.fir.projectStructure.provider.K2IDEProjectStructureProvider
import org.jetbrains.kotlin.idea.base.projectStructure.IDEProjectStructureProvider
import org.jetbrains.kotlin.idea.base.projectStructure.KaSourceModuleKind
import org.jetbrains.kotlin.idea.base.projectStructure.ProjectStructureProviderIdeImpl
import org.jetbrains.kotlin.idea.base.projectStructure.useNewK2ProjectStructureProvider
import org.jetbrains.kotlin.idea.base.util.K1ModeProjectStructureApi
import org.jetbrains.kotlin.library.KotlinLibrary

/**
 * A solution for the transition period, allowing a switch to the old `IdeaModuleInfo`-based implementation `ProjectStructureProviderIdeImpl`
 * implementation via the `kotlin.use.new.project.structure.provider` registry key.
 *
 * Should be removed as a part of KTIJ-32817
 */
internal class DelegatingIDEProjectStructureProvider(project: Project) : IDEProjectStructureProvider() {
    val delegate = if (useNewK2ProjectStructureProvider) {
        K2IDEProjectStructureProvider(project)
    } else {
        @OptIn(K1ModeProjectStructureApi::class)
        ProjectStructureProviderIdeImpl(project)
    }

    override val self: IDEProjectStructureProvider get() = delegate

    override fun getKaSourceModule(moduleId: ModuleId, type: KaSourceModuleKind): KaSourceModule? =
        delegate.getKaSourceModule(moduleId, type)

    override fun getKaSourceModule(moduleEntity: ModuleEntity, type: KaSourceModuleKind): KaSourceModule? =
        delegate.getKaSourceModule(moduleEntity, type)

    override fun getKaSourceModule(openapiModule: Module, type: KaSourceModuleKind): KaSourceModule? =
        delegate.getKaSourceModule(openapiModule, type)

    override fun getKaSourceModuleKind(module: KaSourceModule): KaSourceModuleKind = delegate.getKaSourceModuleKind(module)
    override fun getKaSourceModuleSymbolId(module: KaSourceModule): ModuleId = delegate.getKaSourceModuleSymbolId(module)
    override fun getOpenapiModule(module: KaSourceModule): Module = delegate.getOpenapiModule(module)
    override fun getKaLibraryModules(libraryId: LibraryId): List<KaLibraryModule> = delegate.getKaLibraryModules(libraryId)
    override fun getKaLibraryModules(libraryEntity: LibraryEntity): List<KaLibraryModule> = delegate.getKaLibraryModules(libraryEntity)
    override fun getKaLibraryModules(library: Library): List<KaLibraryModule> = delegate.getKaLibraryModules(library)
    override fun getKaLibraryModuleSymbolicId(libraryModule: KaLibraryModule): LibraryId =
        delegate.getKaLibraryModuleSymbolicId(libraryModule)

    override fun getOpenapiLibrary(module: KaLibraryModule): Library? = delegate.getOpenapiLibrary(module)
    override fun getOpenapiSdk(module: KaLibraryModule): Sdk? = delegate.getOpenapiSdk(module)
    override fun getKaLibraryModule(sdk: Sdk): KaLibraryModule = delegate.getKaLibraryModule(sdk)
    override fun getKotlinLibraries(module: KaLibraryModule): List<KotlinLibrary> = delegate.getKotlinLibraries(module)
    override fun getAssociatedKaModules(virtualFile: VirtualFile): List<KaModule> = delegate.getAssociatedKaModules(virtualFile)
    override fun getForcedKaModule(file: PsiFile): KaModule? = delegate.getForcedKaModule(file)
    override fun setForcedKaModule(file: PsiFile, kaModule: KaModule?) = delegate.setForcedKaModule(file, kaModule)
    override fun getModule(element: PsiElement, useSiteModule: KaModule?): KaModule = delegate.getModule(element, useSiteModule)
    override fun getImplementingModules(module: KaModule): List<KaModule> = delegate.getImplementingModules(module)

    override val globalLanguageVersionSettings: LanguageVersionSettings get() = delegate.globalLanguageVersionSettings
    override val libraryLanguageVersionSettings: LanguageVersionSettings get() = delegate.libraryLanguageVersionSettings

    override fun getNotUnderContentRootModule(project: Project): KaNotUnderContentRootModule {
        // method is protected
        val method = delegate.javaClass.getDeclaredMethod("getNotUnderContentRootModule", Project::class.java)
        method.isAccessible = true
        return method.invoke(delegate, project) as KaNotUnderContentRootModule
    }
}