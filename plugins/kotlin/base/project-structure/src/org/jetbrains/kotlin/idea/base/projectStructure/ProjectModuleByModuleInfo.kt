// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.projectStructure

import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.roots.ProjectRootModificationTracker
import com.intellij.openapi.util.Key
import com.intellij.psi.PsiFile
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.CachedValue
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import org.jetbrains.kotlin.analysis.project.structure.*
import org.jetbrains.kotlin.analyzer.ModuleInfo
import org.jetbrains.kotlin.config.KotlinSourceRootType
import org.jetbrains.kotlin.config.LanguageVersionSettings
import org.jetbrains.kotlin.config.SourceKotlinRootType
import org.jetbrains.kotlin.config.TestSourceKotlinRootType
import org.jetbrains.kotlin.idea.base.projectStructure.moduleInfo.*
import org.jetbrains.kotlin.idea.base.projectStructure.scope.LibrarySourcesScope
import org.jetbrains.kotlin.platform.TargetPlatform
import org.jetbrains.kotlin.resolve.PlatformDependentAnalyzerServices
import java.nio.file.Path
import java.nio.file.Paths

internal abstract class KtModuleByModuleInfoBase(
    moduleInfo: ModuleInfo,
    protected val provider: ProjectStructureProviderIdeImpl
) {
    val ideaModuleInfo = moduleInfo as IdeaModuleInfo

    open val directRegularDependencies: List<KtModule>
        get() = ideaModuleInfo.dependenciesWithoutSelf().map(provider::getKtModuleByModuleInfo).toList()

    open val directDependsOnDependencies: List<KtModule>
        get() = ideaModuleInfo.expectedBy.map(provider::getKtModuleByModuleInfo)

    open val directFriendDependencies: List<KtModule>
        get() = ideaModuleInfo.modulesWhoseInternalsAreVisible().map(provider::getKtModuleByModuleInfo)

    val platform: TargetPlatform get() = ideaModuleInfo.platform
    val analyzerServices: PlatformDependentAnalyzerServices get() = ideaModuleInfo.analyzerServices

    override fun equals(other: Any?): Boolean {
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

internal class KtSourceModuleByModuleInfo(
    private val moduleInfo: ModuleSourceInfo,
    provider: ProjectStructureProviderIdeImpl,
) : KtModuleByModuleInfoBase(moduleInfo, provider), KtSourceModule {

    val ideaModule: Module get() = moduleInfo.module

    override val moduleName: String get() = ideaModule.name

    override val directRegularDependencies: List<KtModule>
        get() = moduleInfo.dependencies(provider)

    override val contentScope: GlobalSearchScope get() = moduleInfo.contentScope

    override val languageVersionSettings: LanguageVersionSettings get() = moduleInfo.module.languageVersionSettings

    override val project: Project get() = ideaModule.project
}

private fun ModuleSourceInfo.dependencies(provider: ProjectStructureProviderIdeImpl): List<KtModule> {
    val sourceRootType = when (this) {
        is ModuleProductionSourceInfo -> SourceKotlinRootType
        is ModuleTestSourceInfo -> TestSourceKotlinRootType
        else -> SourceKotlinRootType
    }
    val key = when (sourceRootType) {
        SourceKotlinRootType -> DependencyKeys.SOURCE_MODULE_DEPENDENCIES
        TestSourceKotlinRootType -> DependencyKeys.TEST_MODULE_DEPENDENCIES
    }
    return CachedValuesManager.getManager(project).getCachedValue(
        module,
        key,
        {
            val dependencies = calculateModuleDependencies(sourceRootType, provider)
            CachedValueProvider.Result.create(dependencies, ProjectRootModificationTracker.getInstance(project))
        },
        false
    )
}

private fun ModuleSourceInfo.calculateModuleDependencies(
    sourceRootType: KotlinSourceRootType,
    provider: ProjectStructureProviderIdeImpl
): List<KtModule> {
    return ModuleDependencyCollector.getInstance(project)
        .collectModuleDependencies(module, platform, sourceRootType, includeExportedDependencies = true)
        .asSequence()
        .filterNot { it == this }
        .map(provider::getKtModuleByModuleInfo)
        .toList()
}

private object DependencyKeys {
    val SOURCE_MODULE_DEPENDENCIES = Key.create<CachedValue<List<KtModule>>>("SOURCE_MODULE_DEPENDENCIES")
    val TEST_MODULE_DEPENDENCIES = Key.create<CachedValue<List<KtModule>>>("TEST_MODULE_DEPENDENCIES")
}

internal class KtLibraryModuleByModuleInfo(
    private val moduleInfo: LibraryInfo,
    provider: ProjectStructureProviderIdeImpl
) : KtModuleByModuleInfoBase(moduleInfo, provider), KtLibraryModule {
    override val libraryName: String
        get() = moduleInfo.library.name ?: "Unnamed library"

    override val librarySources: KtLibrarySourceModule
        get() = moduleInfo.sourcesModuleInfo.let { provider.getKtModuleByModuleInfo(it) as KtLibrarySourceModule }

    override fun getBinaryRoots(): Collection<Path> {
        return moduleInfo.getLibraryRoots().map(Paths::get)
    }

    override val contentScope: GlobalSearchScope get() = ideaModuleInfo.contentScope

    override val project: Project get() = moduleInfo.project
}

internal class SdkKtModuleByModuleInfo(
    private val moduleInfo: SdkInfo,
    provider: ProjectStructureProviderIdeImpl
) : KtModuleByModuleInfoBase(moduleInfo, provider), KtSdkModule {
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
    private val moduleInfo: LibrarySourceInfo,
    provider: ProjectStructureProviderIdeImpl
) : KtModuleByModuleInfoBase(moduleInfo, provider), KtLibrarySourceModule {
    override val libraryName: String
        get() = moduleInfo.library.name ?: "Unnamed library"

    override val directRegularDependencies: List<KtModule>
        get() = binaryLibrary.directRegularDependencies.mapNotNull { (it as? KtLibraryModule)?.librarySources }

    override val directFriendDependencies: List<KtModule>
        get() = binaryLibrary.directFriendDependencies.mapNotNull { (it as? KtLibraryModule)?.librarySources }

    override val directDependsOnDependencies: List<KtModule>
        get() = binaryLibrary.directDependsOnDependencies.mapNotNull { (it as? KtLibraryModule)?.librarySources }

    override val contentScope: GlobalSearchScope
        get() = LibrarySourcesScope(moduleInfo.project, moduleInfo.library)

    override val binaryLibrary: KtLibraryModule
        get() = provider.getKtModuleByModuleInfo(moduleInfo.binariesModuleInfo) as KtLibraryModule

    override val project: Project get() = moduleInfo.project
}


internal class NotUnderContentRootModuleByModuleInfo(
    private val moduleInfo: IdeaModuleInfo,
    provider: ProjectStructureProviderIdeImpl
) : KtModuleByModuleInfoBase(moduleInfo, provider), KtNotUnderContentRootModule {
    override val name: String get() = moduleInfo.name.asString()
    override val file: PsiFile? get() = (moduleInfo as? NotUnderContentRootModuleInfo)?.file
    override val moduleDescription: String get() = "Non under content root module"
    override val contentScope: GlobalSearchScope get() = moduleInfo.contentScope
    override val project: Project get() = moduleInfo.project
}
