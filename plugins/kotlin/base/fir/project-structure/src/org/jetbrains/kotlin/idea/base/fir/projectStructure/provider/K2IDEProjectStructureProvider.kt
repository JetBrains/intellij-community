// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.fir.projectStructure.provider

import com.intellij.java.library.JavaLibraryModificationTracker
import com.intellij.openapi.module.Module
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.ProjectRootModificationTracker
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.util.ModificationTracker
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.backend.workspace.workspaceModel
import com.intellij.platform.workspace.jps.entities.*
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiFileSystemItem
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.parentOfType
import com.intellij.util.containers.ConcurrentFactoryMap
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileKind
import com.intellij.workspaceModel.ide.impl.legacyBridge.library.LibraryBridge
import com.intellij.workspaceModel.ide.impl.legacyBridge.module.findModuleEntity
import com.intellij.workspaceModel.ide.legacyBridge.ModuleBridge
import com.intellij.workspaceModel.ide.legacyBridge.impl.java.JAVA_SOURCE_ROOT_ENTITY_TYPE_ID
import com.intellij.workspaceModel.ide.legacyBridge.impl.java.JAVA_TEST_ROOT_ENTITY_TYPE_ID
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.analysis.api.platform.modification.KotlinModificationTrackerFactory
import org.jetbrains.kotlin.analysis.api.projectStructure.*
import org.jetbrains.kotlin.config.KOTLIN_SOURCE_ROOT_TYPE_ID
import org.jetbrains.kotlin.config.KOTLIN_TEST_ROOT_TYPE_ID
import org.jetbrains.kotlin.idea.KotlinScriptEntity
import org.jetbrains.kotlin.idea.KotlinScriptLibraryEntity
import org.jetbrains.kotlin.idea.base.facet.implementingModules
import org.jetbrains.kotlin.idea.base.fir.projectStructure.FirKaModuleFactory
import org.jetbrains.kotlin.idea.base.fir.projectStructure.modules.KaNotUnderContentRootModuleImpl
import org.jetbrains.kotlin.idea.base.fir.projectStructure.modules.library.KaLibraryEntityBasedLibraryModuleBase
import org.jetbrains.kotlin.idea.base.fir.projectStructure.modules.library.KaLibraryModuleImpl
import org.jetbrains.kotlin.idea.base.fir.projectStructure.modules.library.KaLibrarySdkModuleImpl
import org.jetbrains.kotlin.idea.base.fir.projectStructure.modules.source.KaSourceModuleBase
import org.jetbrains.kotlin.idea.base.fir.projectStructure.modules.source.KaSourceModuleForOutsiderImpl
import org.jetbrains.kotlin.idea.base.fir.projectStructure.symbolicId
import org.jetbrains.kotlin.idea.base.projectStructure.*
import org.jetbrains.kotlin.idea.base.projectStructure.modules.KaSourceModuleForOutsider
import org.jetbrains.kotlin.library.KotlinLibrary
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.utils.exceptions.errorWithAttachment
import org.jetbrains.kotlin.utils.exceptions.withPsiEntry

@ApiStatus.Internal
class K2IDEProjectStructureProvider(private val project: Project) : IDEProjectStructureProvider() {
    override val self: IDEProjectStructureProvider get() = this

    private val cache by lazy(LazyThreadSafetyMode.PUBLICATION) {
        K2IDEProjectStructureProviderCache.getInstance(project)
    }

    override fun getNotUnderContentRootModule(project: Project): KaNotUnderContentRootModule {
        return KaNotUnderContentRootModuleImpl(file = null, project)
    }

    override fun getModule(element: PsiElement, useSiteModule: KaModule?): KaModule {
        ProgressManager.checkCanceled()

        val psiFile: PsiFile? = element.containingFile
        val fileSystemItem = psiFile ?: element.parentOfType<PsiFileSystemItem>(withSelf = true)
        val virtualFile = fileSystemItem?.virtualFile

        virtualFile?.analysisContextModule?.let { return it }

        if (useSiteModule is KaSourceModuleForOutsider || useSiteModule is KaScriptDependencyModule) {
            if (virtualFile != null && virtualFile in useSiteModule.contentScope) {
                return useSiteModule
            }
        }

        if (fileSystemItem != null && cache.isItSafeToCacheModules()) {
            if (psiFile !is KtFile || psiFile.danglingFileResolutionMode == null) {
                return cachedKaModule(fileSystemItem, useSiteModule)
            }
        }
        return computeKaModule(psiFile, virtualFile, useSiteModule)
    }

    internal fun computeKaModule(psiFile: PsiFile?, virtualFile: VirtualFile?, useSiteModule: KaModule?): KaModule {
        if (psiFile != null) {
            computeSpecialModule(psiFile)?.let { return it }
        }

        val candidates = CandidateCollector.collectCandidates(psiFile, virtualFile, project)
            .onEach { ProgressManager.checkCanceled() }
            .flatMap { createKaModules(it) }
            .filter { virtualFile == null || virtualFile in it.contentScope }

        ModuleChooser.chooseModule(candidates, useSiteModule)?.let { return it }
        return KaNotUnderContentRootModuleImpl(psiFile, project)
    }

    private fun createKaModules(data: ModuleCandidate): List<KaModule> = when (data) {
        is ModuleCandidate.Entity -> createKaModule(data.entity, data.fileKind)
        is ModuleCandidate.OutsiderFileEntity -> {
            val kind = data.entity.getKind() ?: return emptyList()
            listOf(
                KaSourceModuleForOutsiderImpl(
                    data.entity.contentRoot.module.symbolicId,
                    kind,
                    fakeVirtualFile = data.fakeVirtualFile,
                    originalVirtualFile = data.originalVirtualFile,
                    project
                ),
            )
        }

        is ModuleCandidate.Sdk -> listOf(cache.cachedKaSdkModule(data.sdkId))
        is ModuleCandidate.FixedModule -> listOf(data.module)
    }

    private fun createKaModule(entity: WorkspaceEntity, fileKind: WorkspaceFileKind): List<KaModule> = when (entity) {
        is SourceRootEntity -> {
            val kind = entity.getKind() ?: return emptyList()
            listOf(
                cache.cachedKaSourceModule(entity.contentRoot.module.symbolicId, kind)
            )
        }

        is LibraryEntity -> {
            val libraryModules = getKaLibraryModules(entity)
            when (fileKind) {
                WorkspaceFileKind.EXTERNAL_SOURCE -> libraryModules.mapNotNull { it.librarySources }
                else -> libraryModules
            }
        }

        is KotlinScriptEntity -> {
            getKaScriptModules(entity)
        }

        is KotlinScriptLibraryEntity -> {
            val libraryModules = getKaScriptLibraryModules(entity)
            when (fileKind) {
                WorkspaceFileKind.EXTERNAL_SOURCE -> libraryModules.mapNotNull { it.librarySources }
                else -> libraryModules
            }
        }

        is SdkEntity -> {
            val module = cache.cachedKaSdkModule(entity.symbolicId)
            when (fileKind) {
                WorkspaceFileKind.EXTERNAL_SOURCE -> listOfNotNull(module.librarySources)
                else -> listOf(module)
            }
        }

        else -> emptyList()
    }

    override fun getImplementingModules(module: KaModule): List<KaModule> {
        when (module) {
            is KaSourceModule -> {
                val implementingIdeaModules = module.openapiModule.implementingModules
                val moduleKind = module.sourceModuleKind
                return implementingIdeaModules.mapNotNull { it.toKaSourceModule(moduleKind) }
            }

            else -> return emptyList()
        }
    }

    override fun getKaSourceModule(moduleId: ModuleId, kind: KaSourceModuleKind): KaSourceModule? {
        val moduleEntity = moduleId.resolve(project.workspaceModel.currentSnapshot) ?: return null
        return getKaSourceModule(moduleEntity, kind)
    }

    override fun getKaSourceModule(
        openapiModule: Module,
        kind: KaSourceModuleKind,
    ): KaSourceModule? {
        val moduleEntity = getModuleEntity(openapiModule) ?: return null
        return getKaSourceModule(moduleEntity, kind)
    }

    override fun getKaSourceModule(moduleEntity: ModuleEntity, kind: KaSourceModuleKind): KaSourceModule =
        cache.cachedKaSourceModule(moduleEntity.symbolicId, kind)

    override fun getKaSourceModules(moduleId: ModuleId): List<KaSourceModule> {
        val moduleEntity = moduleId.resolve(project.workspaceModel.currentSnapshot) ?: return emptyList()
        return getKaSourceModules(moduleEntity)
    }

    override fun getKaSourceModules(moduleEntity: ModuleEntity): List<KaSourceModule> {
        val productionModule = getKaSourceModule(moduleEntity, KaSourceModuleKind.PRODUCTION)
        val testModule = getKaSourceModule(moduleEntity, KaSourceModuleKind.TEST)
        return listOfNotNull(productionModule, testModule)
    }

    override fun getKaSourceModules(openapiModule: Module): List<KaSourceModule> {
        val moduleEntity = getModuleEntity(openapiModule) ?: return emptyList()
        return getKaSourceModules(moduleEntity)
    }

    override fun getKaSourceModuleSymbolId(module: KaSourceModule): ModuleId {
        require(module is KaSourceModuleBase) {
            "Expected ${KaSourceModuleBase::class}, but got ${module::class} instead"
        }
        return module.entityId
    }

    override fun getOpenapiModule(module: KaSourceModule): Module {
        require(module is KaSourceModuleBase) {
            "Expected ${KaSourceModuleBase::class}, but got ${module::class} instead"
        }
        return module.module
    }

    override fun getKaLibraryModules(libraryId: LibraryId): List<KaLibraryModule> {
        val libraryEntity = libraryId.resolve(project.workspaceModel.currentSnapshot) ?: return emptyList()
        return getKaLibraryModules(libraryEntity)
    }

    override fun getKaLibraryModules(libraryEntity: LibraryEntity): List<KaLibraryModule> {
        return listOf(cache.cachedKaLibraryModule(libraryEntity.symbolicId))
    }

    override fun getKaScriptModules(scriptEntity: WorkspaceEntity): List<KaScriptModule> {
        for (factory in FirKaModuleFactory.EP_NAME.extensionList) {
            if (scriptEntity is KotlinScriptEntity) {
                factory.createScriptModule(project, scriptEntity)?.let { return listOf(it) }
            }
        }

        return listOf()
    }

    override fun getKaScriptLibraryModules(libraryEntity: WorkspaceEntity): List<KaLibraryModule> {
        for (factory in FirKaModuleFactory.EP_NAME.extensionList) {
            if (libraryEntity is KotlinScriptLibraryEntity) {
                factory.createScriptLibraryModule(project, libraryEntity)?.let { return listOf(it) }
            }
        }

        return listOf()
    }

    override fun getKaLibraryModule(sdk: Sdk): KaLibraryModule {
        return cache.cachedKaSdkModule(sdk.symbolicId)
    }

    override fun getKaLibraryModuleSymbolicId(libraryModule: KaLibraryModule): LibraryId {
        require(libraryModule is KaLibraryEntityBasedLibraryModuleBase) {
            "Expected ${KaLibraryEntityBasedLibraryModuleBase::class}, but got ${libraryModule::class} instead"
        }
        return libraryModule.entityId
    }

    override fun getOpenapiLibrary(module: KaLibraryModule): Library? {
        return (module as? KaLibraryEntityBasedLibraryModuleBase)?.library
    }

    override fun getOpenapiSdk(module: KaLibraryModule): Sdk? {
        return (module as? KaLibrarySdkModuleImpl)?.sdk
    }

    override fun getAssociatedKaModules(virtualFile: VirtualFile): List<KaModule> {
        return CandidateCollector.collectCandidatesByVirtualFile(virtualFile, project)
            .flatMap { createKaModules(it) }
            .toList()
    }

    override fun getKaLibraryModules(library: Library): List<KaLibraryModule> {
        require(library is LibraryBridge) {
            "Expected ${LibraryBridge::class}, but got ${library::class} instead"
        }
        return getKaLibraryModules(library.libraryId)
    }

    override fun getKotlinLibraries(module: KaLibraryModule): List<KotlinLibrary> {
        if (module !is KaLibraryModuleImpl) return emptyList()
        return module.resolvedKotlinLibraries
    }

    private fun SourceRootEntity.getKind() = when (rootTypeId.name) {
        JAVA_SOURCE_ROOT_ENTITY_TYPE_ID.name, KOTLIN_SOURCE_ROOT_TYPE_ID -> KaSourceModuleKind.PRODUCTION
        JAVA_TEST_ROOT_ENTITY_TYPE_ID.name, KOTLIN_TEST_ROOT_TYPE_ID -> KaSourceModuleKind.TEST
        else -> null
    }

    override fun getCacheDependenciesTracker(): ModificationTracker {
        return ModificationTracker {
            cache.getCacheSdkAndLibrariesTracker().modificationCount + cache.getCacheSourcesTracker().modificationCount
        }
    }

    private fun getModuleEntity(openapiModule: Module): ModuleEntity? {
        require(openapiModule is ModuleBridge) {
            "Expected ${ModuleBridge::class}, but got ${openapiModule::class} instead"
        }
        return openapiModule.findModuleEntity(project.workspaceModel.currentSnapshot)
    }

    companion object {
        fun getInstance(project: Project): K2IDEProjectStructureProvider =
            project.ideProjectStructureProvider.self as K2IDEProjectStructureProvider
    }
}

private fun <T> cachedKaModule(
    anchorElement: PsiFileSystemItem,
    useSiteModule: T?,
): KaModule where T : KaModule {
    val contextToKtModule = CachedValuesManager.getCachedValue(anchorElement) {
        val project = anchorElement.project
        val containingFile = anchorElement.containingFile
        val virtualFile: VirtualFile? = anchorElement.virtualFile
        val isLibraryFile =
            virtualFile?.let { RootKindMatcher.matches(project, it, RootKindFilter.libraryFiles) } ?: false
        val cache = K2IDEProjectStructureProviderCache.getInstance(project)
        val dependencies = if (isLibraryFile) {
            arrayOf(
                ProjectRootModificationTracker.getInstance(project),
                JavaLibraryModificationTracker.getInstance(project),
                cache.getCacheSdkAndLibrariesTracker(),
            )
        } else {
            arrayOf(
                ProjectRootModificationTracker.getInstance(project),
                JavaLibraryModificationTracker.getInstance(project),
                KotlinModificationTrackerFactory.getInstance(project).createProjectWideSourceModificationTracker(),
                cache.getCacheSourcesTracker(),
            )
        }
        CachedValueProvider.Result.create(
            ConcurrentFactoryMap.createMap<T?, KaModule> { context ->
                val projectStructureProvider = K2IDEProjectStructureProvider.getInstance(project)
                projectStructureProvider.computeKaModule(containingFile, virtualFile, context)
            },
            dependencies,
        )
    }

    return contextToKtModule[useSiteModule] ?: errorWithAttachment("No ${KaModule::class.simpleName} found") {
        withPsiEntry("anchorElement", anchorElement)
        withEntry("contextualModule", useSiteModule.toString())
    }
}
