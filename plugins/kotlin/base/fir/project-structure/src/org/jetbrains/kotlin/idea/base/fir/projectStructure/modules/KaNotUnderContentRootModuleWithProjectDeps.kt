// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.fir.projectStructure.modules

import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.OrderEnumerator
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.roots.libraries.Library
import com.intellij.psi.PsiFile
import com.intellij.psi.createSmartPointer
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.analysis.api.platform.projectStructure.KaModuleBase
import org.jetbrains.kotlin.analysis.api.projectStructure.KaModule
import org.jetbrains.kotlin.analysis.api.projectStructure.KaNotUnderContentRootModule
import org.jetbrains.kotlin.idea.base.fir.projectStructure.provider.K2IDEProjectStructureProvider
import org.jetbrains.kotlin.idea.base.projectStructure.KaSourceModuleKind
import org.jetbrains.kotlin.platform.TargetPlatform
import org.jetbrains.kotlin.platform.jvm.JvmPlatforms

@ApiStatus.Internal
class KaNotUnderContentRootModuleWithProjectDeps(
    file: PsiFile?,
    override val project: Project,
): KaNotUnderContentRootModule, KaModuleBase() {
    private val filePointer = file?.createSmartPointer()

    override val name: String = "Non under content root module, but provided with base project libraries dependencies"
    override val moduleDescription: String = name
    override val baseContentScope: GlobalSearchScope
        get() = file?.let { GlobalSearchScope.fileScope(it) } ?: GlobalSearchScope.EMPTY_SCOPE

    override val targetPlatform: TargetPlatform
        get() = JvmPlatforms.defaultJvmPlatform

    override val file: PsiFile?
        get() = filePointer?.element

    override val directRegularDependencies: List<KaModule>
        get() {
            val provider = K2IDEProjectStructureProvider.getInstance(project)
            val ideaModules = ModuleManager.getInstance(project).modules
            val sourceModules = ideaModules.flatMap { module ->
                buildList {
                    provider.getKaSourceModule(module, KaSourceModuleKind.PRODUCTION)?.let { add(it) }
                    provider.getKaSourceModule(module, KaSourceModuleKind.TEST)?.let { add(it) }
                }
            }
            val libraries = LinkedHashSet<Library>()
            val sdks = LinkedHashSet<Sdk>()
            ideaModules.forEach { module ->
                OrderEnumerator.orderEntries(module).librariesOnly().forEachLibrary(libraries::add)
                ModuleRootManager.getInstance(module).sdk?.let(sdks::add)
            }
            ProjectRootManager.getInstance(project).projectSdk?.let(sdks::add)

            val libraryModules = libraries.flatMap { provider.getKaLibraryModules(it) }
            val sdkModules = sdks.map { provider.getKaLibraryModule(it) }

            return buildList {
                addAll(sourceModules)
                addAll(libraryModules)
                addAll(sdkModules)
            }
        }

    override val directDependsOnDependencies: List<KaModule> = emptyList()
    override val directFriendDependencies: List<KaModule> = emptyList()
    override val transitiveDependsOnDependencies: List<KaModule> = emptyList()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        return other is KaNotUnderContentRootModuleWithProjectDeps
                && filePointer == other.filePointer
    }

    override fun hashCode(): Int {
        return filePointer.hashCode()
    }
}