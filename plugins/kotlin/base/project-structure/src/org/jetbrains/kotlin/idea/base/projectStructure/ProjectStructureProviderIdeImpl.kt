// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

@file:OptIn(KaPlatformInterface::class)

package org.jetbrains.kotlin.idea.base.projectStructure

import com.intellij.java.library.JavaLibraryModificationTracker
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.module.Module
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.ProjectRootModificationTracker
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.backend.workspace.workspaceModel
import com.intellij.platform.workspace.jps.entities.LibraryId
import com.intellij.platform.workspace.jps.entities.ModuleId
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.parentOfType
import com.intellij.util.containers.ConcurrentFactoryMap
import com.intellij.workspaceModel.ide.impl.legacyBridge.library.findLibraryBridge
import com.intellij.workspaceModel.ide.legacyBridge.findLibraryEntity
import com.intellij.workspaceModel.ide.legacyBridge.findModule
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaImplementationDetail
import org.jetbrains.kotlin.analysis.api.KaPlatformInterface
import org.jetbrains.kotlin.analysis.api.impl.base.projectStructure.KaBuiltinsModuleImpl
import org.jetbrains.kotlin.analysis.api.platform.modification.KotlinModificationTrackerFactory
import org.jetbrains.kotlin.analysis.api.platform.projectStructure.KotlinProjectStructureProvider
import org.jetbrains.kotlin.analysis.api.projectStructure.*
import org.jetbrains.kotlin.analysis.decompiler.psi.BuiltinsVirtualFileProvider
import org.jetbrains.kotlin.analyzer.ModuleInfo
import org.jetbrains.kotlin.idea.base.facet.platform.platform
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginModeProvider
import org.jetbrains.kotlin.idea.base.projectStructure.moduleInfo.*
import org.jetbrains.kotlin.idea.base.util.K1ModeProjectStructureApi
import org.jetbrains.kotlin.idea.base.util.getOutsiderFileOrigin
import org.jetbrains.kotlin.idea.base.util.isOutsiderFile
import org.jetbrains.kotlin.parsing.KotlinParserDefinition.Companion.STD_SCRIPT_EXT
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.utils.exceptions.errorWithAttachment
import org.jetbrains.kotlin.utils.exceptions.withPsiEntry

@ApiStatus.Internal
interface KaModuleFactory {
    companion object {
        val EP_NAME: ExtensionPointName<KaModuleFactory> =
            ExtensionPointName.create("org.jetbrains.kotlin.ktModuleFactory")
    }

    fun createModule(moduleInfo: ModuleInfo): KaModule?
}

@ApiStatus.Internal
interface ProjectStructureInsightsProvider {
    companion object {
        val EP_NAME: ExtensionPointName<ProjectStructureInsightsProvider> =
            ExtensionPointName.create("org.jetbrains.kotlin.projectStructureInsightsProvider")
    }

    fun isInSpecialSrcDirectory(psiElement: PsiElement): Boolean
}

@ApiStatus.Internal
@K1ModeProjectStructureApi
fun IdeaModuleInfo.toKaModule(): KaModule = ProjectStructureProviderIdeImpl.getKtModuleByModuleInfo(this)

@ApiStatus.Internal
@Suppress("INVISIBLE_REFERENCE", "INVISIBLE_MEMBER")
@K1ModeProjectStructureApi
inline fun <reified T : KaModule> IdeaModuleInfo.toKaModuleOfType(): @kotlin.internal.NoInfer T {
    return toKaModule() as T
}

@OptIn(K1ModeProjectStructureApi::class)
internal class ProjectStructureProviderIdeImpl(private val project: Project) : IDEProjectStructureProvider() {
    @OptIn(KaExperimentalApi::class)
    override fun getModule(element: PsiElement, contextualModule: KaModule?): KaModule {
        ProgressManager.checkCanceled()

        if (contextualModule is KtSourceModuleByModuleInfoForOutsider || contextualModule is KaScriptDependencyModule) {
            val virtualFile = element.containingFile?.virtualFile
            if (virtualFile != null && virtualFile in contextualModule.contentScope) {
                return contextualModule
            }
        }

        val anchorElement = ModuleInfoProvider.findAnchorElement(element)

        // Potentially, we can use any contextualModule,
        // but we select only those modules that can affect the calculation of the result
        // to improve the cache hit rate and reduce it's size
        val crucialContextualModule = when (contextualModule) {
            // Only info-based modules can be used during search
            !is KtModuleByModuleInfoBase -> null

            // KTIJ-27174: to distinguish between script and regular libraries
            is KaScriptModule -> contextualModule

            // KTIJ-27159: to distinguish between libraries with the same content
            is KaSourceModule -> contextualModule

            // KTIJ-27977: a JAR might be shared between several libraries
            is KaLibraryModule, is KaLibrarySourceModule -> contextualModule

            else -> null
        }

        if (anchorElement != null) {
            val containingFile = anchorElement.containingFile
            if (containingFile !is KtFile || containingFile.danglingFileResolutionMode == null) {
                return cachedKtModule(anchorElement, crucialContextualModule)
            }
        }

        return computeModule(element, crucialContextualModule)
    }

    override fun getNotUnderContentRootModule(project: Project): KaNotUnderContentRootModule {
        val moduleInfo = NotUnderContentRootModuleInfo(project, file = null)
        return NotUnderContentRootModuleByModuleInfo(moduleInfo)
    }

    private fun isInSpecialSrcDir(psiElement: PsiElement): Boolean =
        ProjectStructureInsightsProvider.EP_NAME.extensionList.any { it.isInSpecialSrcDirectory(psiElement) }

    fun <T> computeModule(
        psiElement: PsiElement,
        contextualModule: T? = null
    ): KaModule where T : KaModule, T : KtModuleByModuleInfoBase =
        psiElement.containingFile.let { computeModule(it, psiElement, contextualModule, it?.virtualFile) }

    fun <T> computeModule(
        containingFile: PsiFile?,
        psiElement: PsiElement,
        contextualModule: T?,
        virtualFile: VirtualFile?
    ): KaModule where T : KaModule, T : KtModuleByModuleInfoBase {
        if (containingFile != null) {
            computeSpecialModule(containingFile)?.let { return it }
        }

        val moduleInfo = computeModuleInfoForOrdinaryModule(psiElement, contextualModule, virtualFile)

        if (moduleInfo == null && virtualFile != null) {
            computeBuiltinKtModule(virtualFile, psiElement)?.let { return it }
        }

        if (virtualFile != null && isOutsiderFile(virtualFile) && moduleInfo is ModuleSourceInfo) {
            val originalFile = getOutsiderFileOrigin(project, virtualFile)
            return KtSourceModuleByModuleInfoForOutsider(virtualFile, originalFile, moduleInfo)
        }

        return getKtModuleByModuleInfo(moduleInfo ?: NotUnderContentRootModuleInfo(project, psiElement.containingFile as? KtFile))
    }

    @OptIn(KaImplementationDetail::class)
    private fun computeBuiltinKtModule(virtualFile: VirtualFile, psiElement: PsiElement): KaBuiltinsModule? {
        if (virtualFile in BuiltinsVirtualFileProvider.getInstance().getBuiltinVirtualFiles()) {
            val ktElement = psiElement.parentOfType<KtElement>(withSelf = true)
            if (ktElement != null) {
                return KaBuiltinsModuleImpl(ktElement.platform, project)
            }
        }
        return null
    }

    private fun <T> computeModuleInfoForOrdinaryModule(
        psiElement: PsiElement,
        contextualModule: T?,
        virtualFile: VirtualFile?
    ): IdeaModuleInfo? where T : KaModule, T : KtModuleByModuleInfoBase {
        val infoProvider = ModuleInfoProvider.getInstance(project)

        val config = getConfiguration(contextualModule, virtualFile, psiElement)

        val baseModuleInfo = infoProvider.firstOrNull(psiElement, config)
        if (baseModuleInfo != null) {
            return baseModuleInfo
        }

        if (contextualModule != null) {
            val noContextConfig = config.copy(contextualModuleInfo = null)
            val noContextModuleInfo = infoProvider.firstOrNull(psiElement, noContextConfig)
            if (noContextModuleInfo != null) {
                return noContextModuleInfo
            }
        }

        return null
    }

    private fun <T> getConfiguration(
        contextualModule: T?,
        virtualFile: VirtualFile?,
        psiElement: PsiElement
    ): ModuleInfoProvider.Configuration where T : KaModule, T : KtModuleByModuleInfoBase {
        val preferModulesFromExtensions =
            if (KotlinPluginModeProvider.isK2Mode()) {
                isScriptOrItsDependency(contextualModule, virtualFile)
            } else {
                isScriptOrItsDependency(contextualModule, virtualFile) && (!RootKindFilter.projectSources.matches(psiElement) || isInSpecialSrcDir(psiElement))
            }

        return ModuleInfoProvider.Configuration(
            createSourceLibraryInfoForLibraryBinaries = false,
            preferModulesFromExtensions = preferModulesFromExtensions,
            contextualModuleInfo = contextualModule?.ideaModuleInfo,
        )
    }

    override fun getKaSourceModule(
        moduleId: ModuleId,
        type: KaSourceModuleKind
    ): KaSourceModule? {
        val snapshot = project.workspaceModel.currentSnapshot
        val openapiModule = moduleId.resolve(snapshot)?.findModule(snapshot) ?: return null
        return getKaSourceModule(openapiModule, type)
    }

    override fun getKaSourceModuleKind(module: KaSourceModule): KaSourceModuleKind {
        require(module is KtSourceModuleByModuleInfo)
        val moduleInfo = module.moduleInfo as ModuleSourceInfo
        return when (moduleInfo) {
            is ModuleProductionSourceInfo -> KaSourceModuleKind.PRODUCTION
            is ModuleTestSourceInfo -> KaSourceModuleKind.TEST
            else -> error("Unexpected platform: ${moduleInfo.platform}")
        }
    }

    override fun getKaSourceModuleSymbolId(module: KaSourceModule): ModuleId {
        require(module is KtSourceModuleByModuleInfo)
        return module.moduleId
    }

    override fun getKaSourceModule(
        openapiModule: Module,
        type: KaSourceModuleKind
    ): KaSourceModule? {
        val moduleInfo = when (type) {
            KaSourceModuleKind.PRODUCTION -> openapiModule.productionSourceInfo
            KaSourceModuleKind.TEST -> openapiModule.testSourceInfo
        } ?: return null
        return getKtModuleByModuleInfo(moduleInfo) as KtSourceModuleByModuleInfo
    }

    override fun getOpenapiModule(module: KaSourceModule): Module {
        require(module is KtSourceModuleByModuleInfo)
        return module.ideaModule
    }

    override fun getKaLibraryModules(libraryId: LibraryId): List<KaLibraryModule> {
        val snapshot = project.workspaceModel.currentSnapshot
        val library = libraryId.resolve(snapshot)?.findLibraryBridge(snapshot) ?: return emptyList()
        return getKaLibraryModules(library)
    }

    override fun getKaLibraryModules(library: Library): List<KaLibraryModule> {
        return LibraryInfoCache.getInstance(project)[library].map { getKtModuleByModuleInfo(it) as KtLibraryModuleByModuleInfo }
    }


    override fun getKaLibraryModuleSymbolicId(libraryModule: KaLibraryModule): LibraryId {
        require(libraryModule is KtLibraryModuleByModuleInfo)
        return libraryModule.libraryInfo.library.findLibraryEntity(project.workspaceModel.currentSnapshot)?.symbolicId
            ?: error("Cannot find library entity for ${libraryModule.libraryInfo.library.name}")
    }

    override fun getOpenapiLibrary(module: KaLibraryModule): Library? {
        if (module is KtLibraryModuleByModuleInfo) {
            return module.libraryInfo.library
        }
        return null
    }

    override fun getOpenapiSdk(module: KaLibraryModule): Sdk? {
        if (module is KtSdkLibraryModuleByModuleInfo) {
            return module.moduleInfo.sdk
        }
        return null
    }

    override fun getKaLibraryModule(sdk: Sdk): KaLibraryModule {
        val moduleInfo = SdkInfo(project, sdk)
        return getKtModuleByModuleInfo(moduleInfo) as KaLibraryModule
    }

    override fun getContainingKaModules(virtualFile: VirtualFile): List<KaModule> {
        return ModuleInfoProvider.getInstance(project)
            .collectLibraryBinariesModuleInfos(virtualFile)
            .mapTo(mutableListOf()) { getKtModuleByModuleInfo(it) }
    }

    override fun getForcedKaModule(file: PsiFile): KaModule? {
        return file.forcedModuleInfo?.let { getKtModuleByModuleInfo(it) }
    }

    override fun setForcedKaModule(file: PsiFile, kaModule: KaModule?) {
        when (kaModule) {
            null -> {
                file.forcedModuleInfo = null
            }

            is KtModuleByModuleInfoBase -> {
                file.forcedModuleInfo = kaModule.moduleInfo
            }
        }
    }

    companion object {
        // TODO maybe introduce some cache?
        fun getKtModuleByModuleInfo(moduleInfo: ModuleInfo): KaModule {
            return createKtModuleByModuleInfo(moduleInfo)
        }
    }
}

private fun <T> cachedKtModule(
    anchorElement: PsiElement,
    contextualModule: T?,
): KaModule where T : KaModule, T : KtModuleByModuleInfoBase {
    val contextToKtModule = CachedValuesManager.getCachedValue(anchorElement) {
        val project = anchorElement.project
        val containingFile = anchorElement.containingFile
        val virtualFile = containingFile?.virtualFile
        val isLibraryFile =
            virtualFile?.let { RootKindMatcher.matches(project, it, RootKindFilter.libraryFiles) } ?: false
        val dependencies = if (isLibraryFile) {
            arrayOf(
                ProjectRootModificationTracker.getInstance(project),
                JavaLibraryModificationTracker.getInstance(project)
            )
        } else {
            arrayOf(
                ProjectRootModificationTracker.getInstance(project),
                JavaLibraryModificationTracker.getInstance(project),
                KotlinModificationTrackerFactory.getInstance(project).createProjectWideOutOfBlockModificationTracker()
            )
        }
        CachedValueProvider.Result.create(
            ConcurrentFactoryMap.createMap<T?, KaModule> { context ->
                val projectStructureProvider = KotlinProjectStructureProvider.getInstance(project) as ProjectStructureProviderIdeImpl
                projectStructureProvider.computeModule(containingFile, anchorElement, context, virtualFile)
            },
            dependencies,
        )
    }

    return contextToKtModule[contextualModule] ?: errorWithAttachment("No ${KaModule::class.simpleName} found") {
        withPsiEntry("anchorElement", anchorElement)
        withEntry("contextualModule", contextualModule.toString())
    }
}

private inline fun forEachModuleFactory(action: KaModuleFactory.() -> Unit) {
    for (extension in KaModuleFactory.EP_NAME.extensionList) {
        extension.action()
    }
}

private fun createKtModuleByModuleInfo(moduleInfo: ModuleInfo): KaModule {
    forEachModuleFactory {
        createModule(moduleInfo)?.let { return it }
    }

    return when (moduleInfo) {
        is ModuleSourceInfo -> KtSourceModuleByModuleInfo(moduleInfo)
        is NativeKlibLibraryInfo -> KtNativeKlibLibraryModuleByModuleInfo(moduleInfo)
        is LibraryInfo -> KtLibraryModuleByModuleInfo(moduleInfo)
        is SdkInfo -> KtSdkLibraryModuleByModuleInfo(moduleInfo)
        is LibrarySourceInfo -> KtLibrarySourceModuleByModuleInfo(moduleInfo)
        is NotUnderContentRootModuleInfo -> NotUnderContentRootModuleByModuleInfo(moduleInfo)
        else -> NotUnderContentRootModuleByModuleInfo(moduleInfo as IdeaModuleInfo)
    }
}

@OptIn(KaExperimentalApi::class)
private fun <T> isScriptOrItsDependency(contextualModule: T?, virtualFile: VirtualFile?) where T : KaModule, T : KtModuleByModuleInfoBase =
    (contextualModule is KaScriptModule) || (virtualFile?.nameSequence?.endsWith(STD_SCRIPT_EXT) == true)
