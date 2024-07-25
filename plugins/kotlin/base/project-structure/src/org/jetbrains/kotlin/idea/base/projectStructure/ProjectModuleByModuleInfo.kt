// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:OptIn(KaPlatformInterface::class)

package org.jetbrains.kotlin.idea.base.projectStructure

import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.roots.ProjectRootModificationTracker
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.backend.workspace.WorkspaceModel
import com.intellij.platform.backend.workspace.virtualFile
import com.intellij.platform.workspace.jps.entities.LibraryEntity
import com.intellij.platform.workspace.jps.entities.ModuleId
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.GlobalSearchScopes
import com.intellij.psi.util.CachedValue
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.workspaceModel.ide.impl.legacyBridge.library.LibraryBridge
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaPlatformInterface
import org.jetbrains.kotlin.analysis.api.platform.projectStructure.computeTransitiveDependsOnDependencies
import org.jetbrains.kotlin.analysis.api.projectStructure.KaLibraryModule
import org.jetbrains.kotlin.analysis.api.projectStructure.KaLibrarySourceModule
import org.jetbrains.kotlin.analysis.api.projectStructure.KaModule
import org.jetbrains.kotlin.analysis.api.projectStructure.KaNotUnderContentRootModule
import org.jetbrains.kotlin.analysis.api.projectStructure.KaScriptDependencyModule
import org.jetbrains.kotlin.analysis.api.projectStructure.KaSourceModule
import org.jetbrains.kotlin.analyzer.ModuleInfo
import org.jetbrains.kotlin.config.KotlinSourceRootType
import org.jetbrains.kotlin.config.LanguageVersionSettings
import org.jetbrains.kotlin.config.SourceKotlinRootType
import org.jetbrains.kotlin.config.TestSourceKotlinRootType
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.idea.base.projectStructure.forwardDeclarations.kotlinForwardDeclarationsWorkspaceEntity
import org.jetbrains.kotlin.idea.base.projectStructure.moduleInfo.*
import org.jetbrains.kotlin.idea.base.projectStructure.scope.LibrarySourcesScope
import org.jetbrains.kotlin.idea.base.util.minus
import org.jetbrains.kotlin.idea.core.util.toPsiFile
import org.jetbrains.kotlin.platform.TargetPlatform
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.resolve.PlatformDependentAnalyzerServices
import java.nio.file.Path
import java.nio.file.Paths

@ApiStatus.Internal
abstract class KtModuleByModuleInfoBase(moduleInfo: ModuleInfo) {
    val ideaModuleInfo = moduleInfo as IdeaModuleInfo

    open val directRegularDependencies: List<KaModule>
        get() = ideaModuleInfo.dependenciesWithoutSelf().map { it.toKaModule() }.toList()

    open val directDependsOnDependencies: List<KaModule>
        get() = ideaModuleInfo.expectedBy.mapNotNull { (it as? IdeaModuleInfo)?.toKaModule() }

    // TODO: Implement some form of caching. Also see `ProjectStructureProviderIdeImpl.getKtModuleByModuleInfo`.
    val transitiveDependsOnDependencies: List<KaModule>
        get() = computeTransitiveDependsOnDependencies(directDependsOnDependencies)

    open val directFriendDependencies: List<KaModule>
        get() = ideaModuleInfo.modulesWhoseInternalsAreVisible().mapNotNull { (it as? IdeaModuleInfo)?.toKaModule() }

    val targetPlatform: TargetPlatform get() = ideaModuleInfo.platform

    val analyzerServices: PlatformDependentAnalyzerServices get() = ideaModuleInfo.analyzerServices

    override fun equals(other: Any?): Boolean {
        if (this === other) return true

        if (other !is KtModuleByModuleInfoBase) return false
        if (this::class.java != other::class.java) return false
        return this.ideaModuleInfo == other.ideaModuleInfo
    }

    override fun hashCode(): Int {
        return ideaModuleInfo.hashCode()
    }

    @OptIn(KaExperimentalApi::class)
    override fun toString(): String {
        return "${this::class.java.simpleName} ${(this as KaModule).moduleDescription}"
    }
}

@ApiStatus.Internal
open class KtSourceModuleByModuleInfo(private val moduleInfo: ModuleSourceInfo) : KtModuleByModuleInfoBase(moduleInfo), KaSourceModule {
    val ideaModule: Module get() = moduleInfo.module

    override val name: String get() = ideaModule.name

    @KaExperimentalApi
    override val stableModuleName: String? get() = moduleInfo.stableName?.asString()

    val moduleId: ModuleId get() = ModuleId(name)

    override val directRegularDependencies: List<KaModule>
        get() = moduleInfo.collectDependencies(ModuleDependencyCollector.CollectionMode.COLLECT_NON_IGNORED)

    override val contentScope: GlobalSearchScope
        get() = if (moduleInfo is ModuleTestSourceInfo) {
            val testOnlyScope = GlobalSearchScopes.projectTestScope(project).intersectWith(ideaModule.moduleTestSourceScope)
            KotlinResolveScopeEnlarger.enlargeScope(testOnlyScope, ideaModule, isTestScope = true)
        } else
            moduleInfo.contentScope

    override val languageVersionSettings: LanguageVersionSettings get() = moduleInfo.module.languageVersionSettings

    override val project: Project get() = ideaModule.project
}

@ApiStatus.Internal
class KtSourceModuleByModuleInfoForOutsider(
    val fakeVirtualFile: VirtualFile,
    val originalVirtualFile: VirtualFile?,
    moduleInfo: ModuleSourceInfo,
) : KtSourceModuleByModuleInfo(moduleInfo) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is KtSourceModuleByModuleInfoForOutsider || other.fakeVirtualFile != fakeVirtualFile) return false
        return super.equals(other)
    }

    override fun hashCode(): Int = fakeVirtualFile.hashCode()

    override val contentScope: GlobalSearchScope
        get() = adjustContentScope(super.contentScope)

    fun adjustContentScope(scope: GlobalSearchScope): GlobalSearchScope {
        val scopeWithFakeFile = GlobalSearchScope.fileScope(project, fakeVirtualFile).uniteWith(scope)

        return if (originalVirtualFile != null) {
            scopeWithFakeFile.minus(GlobalSearchScope.fileScope(project, originalVirtualFile))
        } else {
            scopeWithFakeFile
        }
    }
}

@ApiStatus.Internal
class KtScriptLibraryModuleByModuleInfo(libraryInfo: LibraryInfo, private val scriptFile: VirtualFile): KtLibraryModuleByModuleInfo(libraryInfo), KaScriptDependencyModule {
    override val file: KtFile?
        get() = PsiManager.getInstance(project).findFile(scriptFile) as? KtFile
}

@ApiStatus.Internal
class KtScriptLibrarySourceModuleByModuleInfo(moduleInfo: LibrarySourceInfo, private val scriptFile: VirtualFile): KtLibrarySourceModuleByModuleInfo(moduleInfo), KaScriptDependencyModule{
    override val file: KtFile?
        get() = PsiManager.getInstance(project).findFile(scriptFile) as? KtFile
}

fun ModuleSourceInfo.collectDependencies(collectionMode: ModuleDependencyCollector.CollectionMode): List<KaModule> {
    val sourceRootType = when (this) {
        is ModuleProductionSourceInfo -> SourceKotlinRootType
        is ModuleTestSourceInfo -> TestSourceKotlinRootType
        else -> SourceKotlinRootType
    }
    val key = when (sourceRootType) {
        SourceKotlinRootType -> when (collectionMode) {
            ModuleDependencyCollector.CollectionMode.COLLECT_IGNORED -> DependencyKeys.SOURCE_MODULE_DEPENDENCIES_IGNORED
            ModuleDependencyCollector.CollectionMode.COLLECT_NON_IGNORED  -> DependencyKeys.SOURCE_MODULE_DEPENDENCIES
        }

        TestSourceKotlinRootType -> when (collectionMode) {
            ModuleDependencyCollector.CollectionMode.COLLECT_IGNORED  -> DependencyKeys.TEST_MODULE_DEPENDENCIES_IGNORED
            ModuleDependencyCollector.CollectionMode.COLLECT_NON_IGNORED  -> DependencyKeys.TEST_MODULE_DEPENDENCIES
        }
    }
    return CachedValuesManager.getManager(project).getCachedValue(
        module,
        key,
        {
            val dependencies = calculateModuleDependencies(sourceRootType, collectionMode)
            CachedValueProvider.Result.create(dependencies, ProjectRootModificationTracker.getInstance(project))
        },
        false
    )
}

private fun ModuleSourceInfo.calculateModuleDependencies(
    sourceRootType: KotlinSourceRootType,
    collectionMode: ModuleDependencyCollector.CollectionMode,
): List<KaModule> {
    return ModuleDependencyCollector.getInstance(project)
        .collectModuleDependencies(module, platform, sourceRootType, includeExportedDependencies = true, collectionMode)
        .asSequence()
        .filterNot { it == this }
        .map { it.toKaModule() }
        .toList()
}

private object DependencyKeys {
    val SOURCE_MODULE_DEPENDENCIES = Key.create<CachedValue<List<KaModule>>>("SOURCE_MODULE_DEPENDENCIES")
    val SOURCE_MODULE_DEPENDENCIES_IGNORED = Key.create<CachedValue<List<KaModule>>>("SOURCE_MODULE_DEPENDENCIES_IGNORED")

    val TEST_MODULE_DEPENDENCIES = Key.create<CachedValue<List<KaModule>>>("TEST_MODULE_DEPENDENCIES")
    val TEST_MODULE_DEPENDENCIES_IGNORED = Key.create<CachedValue<List<KaModule>>>("TEST_MODULE_DEPENDENCIES_IGNORED")
}

@ApiStatus.Internal
open class KtLibraryModuleByModuleInfo(val libraryInfo: LibraryInfo) : KtModuleByModuleInfoBase(libraryInfo), KaLibraryModule {
    override val libraryName: String
        get() = libraryInfo.library.name ?: "Unnamed library"

    override val librarySources: KaLibrarySourceModule
        get() = libraryInfo.sourcesModuleInfo.toKaModuleOfType<KaLibrarySourceModule>()

    override val binaryRoots: Collection<Path>
        get() = libraryInfo.getLibraryRoots().map(Paths::get)

    @KaExperimentalApi
    override val binaryVirtualFiles: Collection<VirtualFile> = emptyList()

    override val isSdk: Boolean get() = false

    override val contentScope: GlobalSearchScope get() = ideaModuleInfo.contentScope

    override val project: Project get() = libraryInfo.project
}

@ApiStatus.Internal
class KtNativeKlibLibraryModuleByModuleInfo(
    private val nativeLibraryInfo: NativeKlibLibraryInfo
) : KtLibraryModuleByModuleInfo(nativeLibraryInfo) {
    override val contentScope: GlobalSearchScope
        get() = GlobalSearchScope.union(
            listOf(mainScope, forwardDeclarationsScope)
        )

    val mainScope: GlobalSearchScope
        get() = nativeLibraryInfo.contentScope

    val forwardDeclarationsScope: GlobalSearchScope
        get() {
            val rootDirectories = getGeneratedFwdDeclarationRoots(nativeLibraryInfo)

            val files = rootDirectories.flatMap { directory ->
                directory.children.filter { it.fileType == KotlinFileType.INSTANCE }
            }.onEach { file ->
                val ktFile = file.toPsiFile(project) as KtFile?
                ktFile?.forcedModuleInfo = nativeLibraryInfo
            }

            return GlobalSearchScope.filesScope(project, files)
        }

    private fun getGeneratedFwdDeclarationRoots(libraryInfo: NativeKlibLibraryInfo): List<VirtualFile> {
        val libraryEntityId = (libraryInfo.library as? LibraryBridge)?.libraryId ?: return emptyList()
        val libraryEntity = WorkspaceModel.getInstance(project).currentSnapshot.resolve(libraryEntityId) ?: return emptyList()
        val vFiles = getGeneratedFwdDeclarationRootsFromEntity(libraryEntity)
        return vFiles
    }

    private fun getGeneratedFwdDeclarationRootsFromEntity(libraryEntity: LibraryEntity): List<VirtualFile> {
        val forwardDeclarationLibraryWorkspaceEntity = libraryEntity.kotlinForwardDeclarationsWorkspaceEntity ?: return emptyList()

        return forwardDeclarationLibraryWorkspaceEntity
            .forwardDeclarationRoots
            .mapNotNull { it.virtualFile }
    }
}

@ApiStatus.Internal
class KtSdkLibraryModuleByModuleInfo(val moduleInfo: SdkInfo) : KtModuleByModuleInfoBase(moduleInfo), KaLibraryModule {
    override val libraryName: String get() = moduleInfo.sdk.name

    override val contentScope: GlobalSearchScope get() = moduleInfo.contentScope

    override val binaryRoots: Collection<Path>
        get() = moduleInfo.sdk.rootProvider.getFiles(OrderRootType.CLASSES).map { virtualFile ->
            Paths.get(virtualFile.fileSystem.extractPresentableUrl(virtualFile.path)).normalize()
        }

    @KaExperimentalApi
    override val binaryVirtualFiles: Collection<VirtualFile> = emptyList()

    override val librarySources: KaLibrarySourceModule? get() = null

    override val isSdk: Boolean get() = true

    override val project: Project get() = moduleInfo.project
}

open class KtLibrarySourceModuleByModuleInfo(
    private val moduleInfo: LibrarySourceInfo
) : KtModuleByModuleInfoBase(moduleInfo), KaLibrarySourceModule {
    override val libraryName: String
        get() = moduleInfo.library.name ?: "Unnamed library"

    override val directRegularDependencies: List<KaModule>
        get() = binaryLibrary.directRegularDependencies.mapNotNull { it as? KaLibraryModule }

    override val directFriendDependencies: List<KaModule>
        get() = binaryLibrary.directFriendDependencies.mapNotNull { it as? KaLibraryModule }

    override val directDependsOnDependencies: List<KaModule>
        get() = binaryLibrary.directDependsOnDependencies.mapNotNull { it as? KaLibraryModule }

    override val contentScope: GlobalSearchScope
        get() = LibrarySourcesScope(moduleInfo.project, moduleInfo.library)

    override val binaryLibrary: KaLibraryModule
        get() = moduleInfo.binariesModuleInfo.toKaModuleOfType<KaLibraryModule>()

    override val project: Project get() = moduleInfo.project
}


class NotUnderContentRootModuleByModuleInfo(
    private val moduleInfo: IdeaModuleInfo
) : KtModuleByModuleInfoBase(moduleInfo), KaNotUnderContentRootModule {
    override val name: String get() = moduleInfo.name.asString()
    override val file: PsiFile? get() = (moduleInfo as? NotUnderContentRootModuleInfo)?.file

    @KaExperimentalApi
    override val moduleDescription: String get() = "Non under content root module"

    override val contentScope: GlobalSearchScope get() = moduleInfo.contentScope
    override val project: Project get() = moduleInfo.project
}
