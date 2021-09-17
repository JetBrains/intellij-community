// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.fir.analysis.project.structure

import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.OrderRootType
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.kotlin.analysis.project.structure.*
import org.jetbrains.kotlin.analyzer.ModuleInfo
import org.jetbrains.kotlin.caches.project.cacheByClassInvalidatingOnRootModifications
import org.jetbrains.kotlin.config.LanguageVersionSettings
import org.jetbrains.kotlin.idea.caches.project.*
import org.jetbrains.kotlin.idea.project.languageVersionSettings
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

    val directRefinementDependencies: List<KtModule>
        get() = ideaModuleInfo.expectedBy.map(provider::getKtModuleByModuleInfo)

    val directFriendDependencies: List<KtModule>
        get() = ideaModuleInfo.modulesWhoseInternalsAreVisible().map(provider::getKtModuleByModuleInfo)

    val contentScope: GlobalSearchScope get() = ideaModuleInfo.contentScope()
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
        get() = moduleInfo.module.cacheByClassInvalidatingOnRootModifications(KtSourceModuleByModuleInfo::class.java) {
            ideaModule
                .getSourceModuleDependencies(
                    forProduction = moduleInfo is ModuleProductionSourceInfo,
                    moduleInfo.platform,
                    includeTransitiveDependencies = false
                )
                .asSequence()
                .filterNot { it == moduleInfo }
                .map(provider::getKtModuleByModuleInfo)
                .toList()
        }

    override val languageVersionSettings: LanguageVersionSettings get() = moduleInfo.module.languageVersionSettings

    override val project: Project get() = ideaModule.project
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

    override val project: Project get() = moduleInfo.project
}

internal class SdkKtModuleByModuleInfo(
    private val moduleInfo: SdkInfo,
    provider: ProjectStructureProviderIdeImpl
) : KtModuleByModuleInfoBase(moduleInfo, provider), KtSdkModule {
    override val sdkName: String
        get() = moduleInfo.sdk.name

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

    override val binaryLibrary: KtLibraryModule
        get() = provider.getKtModuleByModuleInfo(moduleInfo.binariesModuleInfo) as KtLibraryModule

    override val project: Project get() = moduleInfo.project
}


internal class NotUnderContentRootModuleByModuleInfo(
    moduleInfo: NotUnderContentRootModuleInfo,
    provider: ProjectStructureProviderIdeImpl
) : KtModuleByModuleInfoBase(moduleInfo, provider), KtNotUnderContentRootModule {
    override val moduleDescription: String
        get() = "Non under content root module"
}


private fun ModuleInfo.dependenciesWithoutSelf(): Sequence<ModuleInfo> =
    dependencies().asSequence().filter { it != this }