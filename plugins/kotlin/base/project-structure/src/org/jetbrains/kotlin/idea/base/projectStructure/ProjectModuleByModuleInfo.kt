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
import org.jetbrains.kotlin.analysis.api.projectStructure.*
import org.jetbrains.kotlin.analyzer.ModuleInfo
import org.jetbrains.kotlin.config.KotlinSourceRootType
import org.jetbrains.kotlin.config.LanguageVersionSettings
import org.jetbrains.kotlin.config.SourceKotlinRootType
import org.jetbrains.kotlin.config.TestSourceKotlinRootType
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.idea.base.projectStructure.DependencyKeys.SOURCE_MODULE_DEPENDENCIES
import org.jetbrains.kotlin.idea.base.projectStructure.DependencyKeys.SOURCE_MODULE_DEPENDENCIES_IGNORED
import org.jetbrains.kotlin.idea.base.projectStructure.DependencyKeys.TEST_MODULE_DEPENDENCIES
import org.jetbrains.kotlin.idea.base.projectStructure.DependencyKeys.TEST_MODULE_DEPENDENCIES_IGNORED
import org.jetbrains.kotlin.idea.base.projectStructure.forwardDeclarations.kotlinForwardDeclarationsWorkspaceEntity
import org.jetbrains.kotlin.idea.base.projectStructure.moduleInfo.*
import org.jetbrains.kotlin.idea.base.projectStructure.scope.LibrarySourcesScope
import org.jetbrains.kotlin.idea.base.projectStructure.util.createAtomicReferenceFieldUpdaterForProperty
import org.jetbrains.kotlin.idea.base.util.K1ModeProjectStructureApi
import org.jetbrains.kotlin.idea.base.util.minus
import org.jetbrains.kotlin.idea.core.util.toPsiFile
import org.jetbrains.kotlin.platform.TargetPlatform
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.resolve.PlatformDependentAnalyzerServices
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater

@ApiStatus.Internal
@K1ModeProjectStructureApi
abstract class KtModuleByModuleInfoBase(moduleInfo: ModuleInfo) {
    @Volatile
    private var _directRegularDependencies: List<KaModule>? = null

    @Volatile
    private var _directFriendDependencies: List<KaModule>? = null

    @Volatile
    private var _directDependsOnDependencies: List<KaModule>? = null

    @Volatile
    private var _transitiveDependsOnDependencies: List<KaModule>? = null

    val ideaModuleInfo: IdeaModuleInfo = moduleInfo as IdeaModuleInfo

    val directRegularDependencies: List<KaModule>
        get() {
            _directRegularDependencies?.let { return it }

            val list = computeDirectRegularDependencies()
            return if (directRegularDependenciesUpdater.compareAndSet(this, null, list)) {
                list
            } else {
                _directRegularDependencies!!
            }
        }

    protected open fun computeDirectRegularDependencies(): List<KaModule> =
        ideaModuleInfo.dependenciesWithoutSelf().mapTo(ArrayList()) { it.toKaModule() }
            .also { it.trimToSize() }

    val directDependsOnDependencies: List<KaModule>
        get() {
            _directDependsOnDependencies?.let { return it }

            val list = computeDirectDependsOnDependencies()
            return if (directDependsOnDependenciesUpdater.compareAndSet(this, null, list)) {
                list
            } else {
                _directDependsOnDependencies!!
            }
        }

    protected open fun computeDirectDependsOnDependencies(): List<KaModule> =
        ideaModuleInfo.expectedBy.mapNotNull { (it as? IdeaModuleInfo)?.toKaModule() }

    val transitiveDependsOnDependencies: List<KaModule>
        get() {
            _transitiveDependsOnDependencies?.let { return it }

            val list = computeTransitiveDependsOnDependencies(directDependsOnDependencies)
            return if (transitiveDependsOnDependenciesUpdater.compareAndSet(this, null, list)) {
                list
            } else {
                _transitiveDependsOnDependencies!!
            }
        }

    val directFriendDependencies: List<KaModule>
        get() {
            _directFriendDependencies?.let { return it }

            val list = computeDirectFriendDependencies()
            return if (directFriendDependenciesUpdater.compareAndSet(this, null, list)) {
                list
            } else {
                _directFriendDependencies!!
            }
        }

    protected open fun computeDirectFriendDependencies(): List<KaModule> =
        ideaModuleInfo.modulesWhoseInternalsAreVisible().mapNotNull { (it as? IdeaModuleInfo)?.toKaModule() }

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

    companion object {
        @JvmStatic
        private val directRegularDependenciesUpdater: AtomicReferenceFieldUpdater<KtModuleByModuleInfoBase, List<KaModule>?> =
            createAtomicReferenceFieldUpdaterForProperty(
                KtModuleByModuleInfoBase::_directRegularDependencies
            )

        @JvmStatic
        private val directFriendDependenciesUpdater: AtomicReferenceFieldUpdater<KtModuleByModuleInfoBase, List<KaModule>?> =
            createAtomicReferenceFieldUpdaterForProperty(
                KtModuleByModuleInfoBase::_directFriendDependencies
            )

        @JvmStatic
        private val directDependsOnDependenciesUpdater: AtomicReferenceFieldUpdater<KtModuleByModuleInfoBase, List<KaModule>?> =
            createAtomicReferenceFieldUpdaterForProperty(
                KtModuleByModuleInfoBase::_directDependsOnDependencies
            )

        @JvmStatic
        private val transitiveDependsOnDependenciesUpdater: AtomicReferenceFieldUpdater<KtModuleByModuleInfoBase, List<KaModule>?> =
            createAtomicReferenceFieldUpdaterForProperty(
                KtModuleByModuleInfoBase::_transitiveDependsOnDependencies
            )
    }
}

@ApiStatus.Internal
@K1ModeProjectStructureApi
open class KtSourceModuleByModuleInfo(private val moduleInfo: ModuleSourceInfo) : KtModuleByModuleInfoBase(moduleInfo), KaSourceModule {
    val ideaModule: Module get() = moduleInfo.module

    override val name: String get() = ideaModule.name

    @KaExperimentalApi
    override val stableModuleName: String? get() = moduleInfo.stableName?.asString()

    val moduleId: ModuleId get() = ModuleId(name)

    override fun computeDirectRegularDependencies(): List<KaModule> =
        moduleInfo.collectDependencies(ModuleDependencyCollector.CollectionMode.COLLECT_NON_IGNORED)

    override val contentScope: GlobalSearchScope
        get() = moduleInfo.contentScope

    override val languageVersionSettings: LanguageVersionSettings get() = moduleInfo.module.languageVersionSettings

    override val project: Project get() = ideaModule.project
}

@ApiStatus.Internal
@K1ModeProjectStructureApi
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
@K1ModeProjectStructureApi
class KtScriptLibraryModuleByModuleInfo(libraryInfo: LibraryInfo, override val file: KtFile? = null):
    KtLibraryModuleByModuleInfo(libraryInfo), KaScriptDependencyModule

@ApiStatus.Internal
@K1ModeProjectStructureApi
class KtScriptLibrarySourceModuleByModuleInfo(moduleInfo: LibrarySourceInfo, override val file: KtFile? = null):
    KtLibrarySourceModuleByModuleInfo(moduleInfo), KaScriptDependencyModule

@K1ModeProjectStructureApi
fun ModuleSourceInfo.collectDependencies(collectionMode: ModuleDependencyCollector.CollectionMode): List<KaModule> {
    val sourceRootType = when (this) {
        is ModuleProductionSourceInfo -> SourceKotlinRootType
        is ModuleTestSourceInfo -> TestSourceKotlinRootType
        else -> SourceKotlinRootType
    }
    val key = when (sourceRootType) {
        SourceKotlinRootType -> when (collectionMode) {
            ModuleDependencyCollector.CollectionMode.COLLECT_IGNORED -> SOURCE_MODULE_DEPENDENCIES_IGNORED
            ModuleDependencyCollector.CollectionMode.COLLECT_NON_IGNORED  -> SOURCE_MODULE_DEPENDENCIES
        }

        TestSourceKotlinRootType -> when (collectionMode) {
            ModuleDependencyCollector.CollectionMode.COLLECT_IGNORED  -> TEST_MODULE_DEPENDENCIES_IGNORED
            ModuleDependencyCollector.CollectionMode.COLLECT_NON_IGNORED  -> TEST_MODULE_DEPENDENCIES
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
    private val map = ConcurrentHashMap<String, Key<CachedValue<List<KaModule>>>>()

    private fun getKey(key: String, moduleInfo: ModuleSourceInfo): Key<CachedValue<List<KaModule>>> =
        (key + moduleInfo.javaClass.name).let { key ->
            map.computeIfAbsent(key) { Key.create(it) }
        }

    val ModuleSourceInfo.SOURCE_MODULE_DEPENDENCIES get() = getKey("SOURCE_MODULE_DEPENDENCIES", this)
    val ModuleSourceInfo.SOURCE_MODULE_DEPENDENCIES_IGNORED get() = getKey("SOURCE_MODULE_DEPENDENCIES_IGNORED", this)

    val ModuleSourceInfo.TEST_MODULE_DEPENDENCIES get() = getKey("TEST_MODULE_DEPENDENCIES", this)
    val ModuleSourceInfo.TEST_MODULE_DEPENDENCIES_IGNORED get() = getKey("TEST_MODULE_DEPENDENCIES_IGNORED", this)
}

@ApiStatus.Internal
@K1ModeProjectStructureApi
open class KtLibraryModuleByModuleInfo(val libraryInfo: LibraryInfo) : KtModuleByModuleInfoBase(libraryInfo), KaLibraryModule {
    @Volatile
    private var _librarySources: KaLibrarySourceModule? = null

    override val libraryName: String
        get() = libraryInfo.library.name ?: "Unnamed library"

    override val librarySources: KaLibrarySourceModule
        get() {
            _librarySources?.let { return it }

            val library = libraryInfo.sourcesModuleInfo.toKaModuleOfType<KaLibrarySourceModule>()
            return if (librarySourcesUpdater.compareAndSet(this, null, library)) {
                library
            } else {
                _librarySources!!
            }
        }

    override val binaryRoots: Collection<Path>
        get() = binaryVirtualFiles.map { it.toNioPath() }

    @KaExperimentalApi
    override val binaryVirtualFiles: Collection<VirtualFile> =
        libraryInfo.library.getFiles(OrderRootType.CLASSES).toList()

    override val isSdk: Boolean get() = false

    override val contentScope: GlobalSearchScope get() = ideaModuleInfo.contentScope

    override val project: Project get() = libraryInfo.project

    companion object {
        @JvmStatic
        private val librarySourcesUpdater: AtomicReferenceFieldUpdater<KtLibraryModuleByModuleInfo, KaLibrarySourceModule?> =
            createAtomicReferenceFieldUpdaterForProperty(
                KtLibraryModuleByModuleInfo::_librarySources
            )
    }
}

@ApiStatus.Internal
@K1ModeProjectStructureApi
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
@K1ModeProjectStructureApi
class KtSdkLibraryModuleByModuleInfo(val moduleInfo: SdkInfo) : KtModuleByModuleInfoBase(moduleInfo), KaLibraryModule {
    override val libraryName: String get() = moduleInfo.sdk.name

    override val contentScope: GlobalSearchScope get() = moduleInfo.contentScope

    override val binaryRoots: Collection<Path>
        get() = binaryVirtualFiles.map { virtualFile ->
            Paths.get(virtualFile.fileSystem.extractPresentableUrl(virtualFile.path)).normalize()
        }

    @KaExperimentalApi
    override val binaryVirtualFiles: Collection<VirtualFile> =
        moduleInfo.sdk.rootProvider.getFiles(OrderRootType.CLASSES).toList()

    override val librarySources: KaLibrarySourceModule? get() = null

    override val isSdk: Boolean get() = true

    override val project: Project get() = moduleInfo.project
}

@K1ModeProjectStructureApi
open class KtLibrarySourceModuleByModuleInfo(
    private val moduleInfo: LibrarySourceInfo
) : KtModuleByModuleInfoBase(moduleInfo), KaLibrarySourceModule {

    @Volatile
    private var _binaryLibrary: KaLibraryModule? = null

    override val libraryName: String
        get() = moduleInfo.library.name ?: "Unnamed library"

    override fun computeDirectRegularDependencies(): List<KaModule> =
        binaryLibrary.directRegularDependencies.mapNotNull { it as? KaLibraryModule }

    override fun computeDirectFriendDependencies(): List<KaModule> =
        binaryLibrary.directFriendDependencies.mapNotNull { it as? KaLibraryModule }

    override fun computeDirectDependsOnDependencies(): List<KaModule> =
        binaryLibrary.directDependsOnDependencies.mapNotNull { it as? KaLibraryModule }

    override val contentScope: GlobalSearchScope
        get() = LibrarySourcesScope(moduleInfo.project, moduleInfo.library)

    override val binaryLibrary: KaLibraryModule
        get() {
            _binaryLibrary?.let { return it }

            val library = moduleInfo.binariesModuleInfo.toKaModuleOfType<KaLibraryModule>()
            return if (binaryLibraryUpdater.compareAndSet(this, null, library)) {
                library
            } else {
                _binaryLibrary!!
            }
        }

    override val project: Project get() = moduleInfo.project

    companion object {
        @JvmStatic
        private val binaryLibraryUpdater: AtomicReferenceFieldUpdater<KtLibrarySourceModuleByModuleInfo, KaLibraryModule?> =
            createAtomicReferenceFieldUpdaterForProperty(
                KtLibrarySourceModuleByModuleInfo::_binaryLibrary
            )
    }
}


@K1ModeProjectStructureApi
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
