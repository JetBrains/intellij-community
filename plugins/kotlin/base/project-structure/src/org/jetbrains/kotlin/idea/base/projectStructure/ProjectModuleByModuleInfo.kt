// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.projectStructure

import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.roots.ProjectRootModificationTracker
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.workspace.jps.entities.ModuleId
import com.intellij.psi.PsiFile
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.GlobalSearchScopes
import com.intellij.psi.util.CachedValue
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.analysis.project.structure.*
import org.jetbrains.kotlin.analyzer.ModuleInfo
import org.jetbrains.kotlin.config.KotlinSourceRootType
import org.jetbrains.kotlin.config.LanguageVersionSettings
import org.jetbrains.kotlin.config.SourceKotlinRootType
import org.jetbrains.kotlin.config.TestSourceKotlinRootType
import org.jetbrains.kotlin.idea.base.projectStructure.moduleInfo.*
import org.jetbrains.kotlin.idea.base.projectStructure.scope.LibrarySourcesScope
import org.jetbrains.kotlin.idea.base.util.minus
import org.jetbrains.kotlin.platform.TargetPlatform
import org.jetbrains.kotlin.resolve.PlatformDependentAnalyzerServices
import java.nio.file.Path
import java.nio.file.Paths

@ApiStatus.Internal
abstract class KtModuleByModuleInfoBase(moduleInfo: ModuleInfo) {
    val ideaModuleInfo = moduleInfo as IdeaModuleInfo

    open val directRegularDependencies: List<KtModule>
        get() = ideaModuleInfo.dependenciesWithoutSelf().map { it.toKtModule() }.toList()

    open val directDependsOnDependencies: List<KtModule>
        get() = ideaModuleInfo.expectedBy.mapNotNull { (it as? IdeaModuleInfo)?.toKtModule() }

    // TODO: Implement some form of caching. Also see `ProjectStructureProviderIdeImpl.getKtModuleByModuleInfo`.
    val transitiveDependsOnDependencies: List<KtModule>
        get() = computeTransitiveDependsOnDependencies(directDependsOnDependencies)

    open val directFriendDependencies: List<KtModule>
        get() = ideaModuleInfo.modulesWhoseInternalsAreVisible().mapNotNull { (it as? IdeaModuleInfo)?.toKtModule() }

    val platform: TargetPlatform get() = ideaModuleInfo.platform
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

    override fun toString(): String {
        return "${this::class.java.simpleName} ${(this as KtModule).moduleDescription}"
    }
}

@ApiStatus.Internal
open class KtSourceModuleByModuleInfo(private val moduleInfo: ModuleSourceInfo) : KtModuleByModuleInfoBase(moduleInfo), KtSourceModule {
    val ideaModule: Module get() = moduleInfo.module

    override val moduleName: String get() = ideaModule.name

    override val stableModuleName: String? get() = moduleInfo.stableName?.asString()

    val moduleId: ModuleId get() = ModuleId(moduleName)

    override val directRegularDependencies: List<KtModule>
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

fun ModuleSourceInfo.collectDependencies(collectionMode: ModuleDependencyCollector.CollectionMode): List<KtModule> {
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
): List<KtModule> {
    return ModuleDependencyCollector.getInstance(project)
        .collectModuleDependencies(module, platform, sourceRootType, includeExportedDependencies = true, collectionMode)
        .asSequence()
        .filterNot { it == this }
        .map { it.toKtModule() }
        .toList()
}

private object DependencyKeys {
    val SOURCE_MODULE_DEPENDENCIES = Key.create<CachedValue<List<KtModule>>>("SOURCE_MODULE_DEPENDENCIES")
    val SOURCE_MODULE_DEPENDENCIES_IGNORED = Key.create<CachedValue<List<KtModule>>>("SOURCE_MODULE_DEPENDENCIES_IGNORED")

    val TEST_MODULE_DEPENDENCIES = Key.create<CachedValue<List<KtModule>>>("TEST_MODULE_DEPENDENCIES")
    val TEST_MODULE_DEPENDENCIES_IGNORED = Key.create<CachedValue<List<KtModule>>>("TEST_MODULE_DEPENDENCIES_IGNORED")
}

@ApiStatus.Internal
class KtLibraryModuleByModuleInfo(val libraryInfo: LibraryInfo) : KtModuleByModuleInfoBase(libraryInfo), KtLibraryModule {
    override val libraryName: String
        get() = libraryInfo.library.name ?: "Unnamed library"

    override val librarySources: KtLibrarySourceModule
        get() = libraryInfo.sourcesModuleInfo.toKtModuleOfType<KtLibrarySourceModule>()

    override fun getBinaryRoots(): Collection<Path> {
        return libraryInfo.getLibraryRoots().map(Paths::get)
    }

    override val contentScope: GlobalSearchScope get() = ideaModuleInfo.contentScope

    override val project: Project get() = libraryInfo.project
}

@ApiStatus.Internal
class SdkKtModuleByModuleInfo(val moduleInfo: SdkInfo) : KtModuleByModuleInfoBase(moduleInfo), KtSdkModule {
    override val sdkName: String
        get() = moduleInfo.sdk.name

    override val contentScope: GlobalSearchScope get() = moduleInfo.contentScope

    override fun getBinaryRoots(): Collection<Path> {
        return moduleInfo.sdk.rootProvider.getFiles(OrderRootType.CLASSES).map { virtualFile ->
            Paths.get(virtualFile.fileSystem.extractPresentableUrl(virtualFile.path)).normalize()
        }
    }

    override val project: Project get() = moduleInfo.project
}

internal class KtLibrarySourceModuleByModuleInfo(
    private val moduleInfo: LibrarySourceInfo
) : KtModuleByModuleInfoBase(moduleInfo), KtLibrarySourceModule {
    override val libraryName: String
        get() = moduleInfo.library.name ?: "Unnamed library"

    override val directRegularDependencies: List<KtModule>
        get() = binaryLibrary.directRegularDependencies.mapNotNull { it as? KtLibraryModule }

    override val directFriendDependencies: List<KtModule>
        get() = binaryLibrary.directFriendDependencies.mapNotNull { it as? KtLibraryModule }

    override val directDependsOnDependencies: List<KtModule>
        get() = binaryLibrary.directDependsOnDependencies.mapNotNull { it as? KtLibraryModule }

    override val contentScope: GlobalSearchScope
        get() = LibrarySourcesScope(moduleInfo.project, moduleInfo.library)

    override val binaryLibrary: KtLibraryModule
        get() = moduleInfo.binariesModuleInfo.toKtModuleOfType<KtLibraryModule>()

    override val project: Project get() = moduleInfo.project
}


internal class NotUnderContentRootModuleByModuleInfo(
    private val moduleInfo: IdeaModuleInfo
) : KtModuleByModuleInfoBase(moduleInfo), KtNotUnderContentRootModule {
    override val name: String get() = moduleInfo.name.asString()
    override val file: PsiFile? get() = (moduleInfo as? NotUnderContentRootModuleInfo)?.file
    override val moduleDescription: String get() = "Non under content root module"
    override val contentScope: GlobalSearchScope get() = moduleInfo.contentScope
    override val project: Project get() = moduleInfo.project
}
