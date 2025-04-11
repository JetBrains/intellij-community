// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.fir.projectStructure.modules.source

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.roots.TestModuleProperties
import com.intellij.platform.workspace.jps.entities.*
import org.jetbrains.kotlin.analysis.api.projectStructure.KaLibraryModule
import org.jetbrains.kotlin.analysis.api.projectStructure.KaModule
import org.jetbrains.kotlin.analysis.api.projectStructure.KaSourceModule
import org.jetbrains.kotlin.caches.project.cacheByClassInvalidatingOnRootModifications
import org.jetbrains.kotlin.idea.base.facet.additionalVisibleModules
import org.jetbrains.kotlin.idea.base.facet.implementedModules
import org.jetbrains.kotlin.idea.base.fir.projectStructure.KotlinExportedDependenciesCollector
import org.jetbrains.kotlin.idea.base.fir.projectStructure.modules.library.KaLibraryModuleImpl
import org.jetbrains.kotlin.idea.base.projectStructure.*
import org.jetbrains.kotlin.idea.base.projectStructure.kmp.HmppSourceModuleDependencyFilter
import org.jetbrains.kotlin.idea.base.projectStructure.kmp.SourceModuleDependenciesFilterCandidate
import org.jetbrains.kotlin.konan.library.KONAN_STDLIB_NAME
import org.jetbrains.kotlin.utils.addIfNotNull
import org.jetbrains.kotlin.utils.closure
import com.intellij.openapi.module.Module as OpenapiModule

/**
 * A utility service for computing all types of dependencies for a [KaModule].
 *
 * This class encapsulates the large logic of dependency computation in one place.
 * It does not cache the results of the computation.
 */
@Service(Service.Level.PROJECT)
internal class KaSourceModuleDependenciesProvider(private val project: Project) {

    fun getDirectRegularDependencies(from: KaSourceModuleBase, extraDependencies: List<KaModule>): List<KaModule> {
        return buildSet<KaModule> {
            for (dependency in from.entity.dependencies) {
                dependency.collectKaModules(from.kind, this)
            }
            removeIf { !from.canDependOn(it) }
            addAll(extraDependencies)
            remove(from)
        }.toList()
    }

    fun getDirectDependsOnDependencies(from: KaSourceModuleBase): List<KaModule> {
        return from.module.implementedModules.mapNotNull { it.toKaSourceModule(from.kind) }
    }

    fun getDirectFriendDependencies(from: KaSourceModuleBase): List<KaModule> {
        val openapiModule = from.module
        val cacheKey = when (from.kind) {
            KaSourceModuleKind.PRODUCTION -> when (from) {
                is KaSourceModuleImpl -> ProductionKeyForModulesWhoseInternalsAreVisible.RegularSourceModule::class.java
                is KaSourceModuleForOutsiderImpl -> ProductionKeyForModulesWhoseInternalsAreVisible.OutsiderSourceModule::class.java
                else -> error("Unexpected ${KaSourceModuleBase::class}: $from")
            }
            KaSourceModuleKind.TEST ->  when (from) {
                is KaSourceModuleImpl -> TestKeyForModulesWhoseInternalsAreVisible.RegularSourceModule::class.java
                is KaSourceModuleForOutsiderImpl -> TestKeyForModulesWhoseInternalsAreVisible.OutsiderSourceModule::class.java
                else -> error("Unexpected ${KaSourceModuleBase::class}: $from")
            }
        }
        /**
        [getDirectFriendDependencies] may have itself in the result, so we remove it.
        For [KaSourceModuleForOutsiderImpl], it will be the original module
         */
        val extraModule = when (from) {
            is KaSourceModuleImpl -> from
            is KaSourceModuleForOutsiderImpl -> openapiModule.toKaSourceModule(from.sourceModuleKind)
            else -> error("Unexpected ${KaSourceModuleBase::class}: $from")
        }
        return (getDirectFriendDependencies(openapiModule, from.kind, cacheKey) - listOfNotNull(extraModule)).toList()
    }

    /**
     * may return self in a result
     */
    private fun getDirectFriendDependencies(
        openapiModule: OpenapiModule,
        kind: KaSourceModuleKind,
        cacheKey: Class<*>,
    ): Set<KaModule> {
        return when (kind) {
            KaSourceModuleKind.PRODUCTION -> openapiModule.cacheByClassInvalidatingOnRootModifications(cacheKey) {
                openapiModule.additionalVisibleModules
                    .mapNotNullTo(mutableSetOf()) { it.toKaSourceModuleForProduction() }
                    .ifEmpty { emptySet() }
            }

            KaSourceModuleKind.TEST -> openapiModule.cacheByClassInvalidatingOnRootModifications(cacheKey) {
                val result = linkedSetOf<KaModule>()
                result.addIfNotNull(openapiModule.toKaSourceModuleForProduction())
                TestModuleProperties.getInstance(openapiModule).productionModule?.let {
                    result.addIfNotNull(it.toKaSourceModuleForProduction())
                }
                result.addAll(result.closure { dependency ->
                    if (dependency is KaSourceModule) {
                        dependency.openapiModule.implementedModules.mapNotNull {
                            it.toKaSourceModule(dependency.sourceModuleKind)
                        }
                    } else {
                        emptyList()
                    }
                })
                for (additionalVisibleModule in openapiModule.additionalVisibleModules) {
                    val productionModule = additionalVisibleModule.toKaSourceModuleForProduction()
                    if (productionModule != null) {
                        result.add(productionModule)
                        continue
                    }
                    val originalModuleHasTestRoot = openapiModule.toKaSourceModuleForTest() != null
                    if (originalModuleHasTestRoot) {
                        // we should consider `testFixture` as an additional visible module for test sources
                        result.addIfNotNull(additionalVisibleModule.toKaSourceModuleForTest())
                    }
                }
                result.ifEmpty { emptySet() }
            }
        }
    }

    private fun ModuleDependencyItem.collectKaModules(kind: KaSourceModuleKind, to: MutableCollection<KaModule>) {
        when (this) {
            is ModuleDependency -> collectDependenciesWithExported(kind, to)
            is LibraryDependency -> {
                when (scope) {
                    DependencyScope.COMPILE, DependencyScope.PROVIDED -> {
                        to += library.toKaLibraryModules(project)
                    }

                    DependencyScope.TEST -> {
                        if (kind == KaSourceModuleKind.TEST) {
                            to += library.toKaLibraryModules(project)
                        }
                    }

                    DependencyScope.RUNTIME -> {}
                }
            }

            is SdkDependency -> {
                val sdk = ProjectJdkTable.getInstance().findJdk(sdk.name, sdk.type) ?: return
                to += sdk.toKaLibraryModule(project)
            }

            InheritedSdkDependency -> {
                val sdk = ProjectRootManager.getInstance(project).projectSdk ?: return
                to += sdk.toKaLibraryModule(project)
            }

            ModuleSourceDependency -> {}
        }
    }

    private fun ModuleDependency.collectDependenciesWithExported(kind: KaSourceModuleKind, to: MutableCollection<KaModule>) {
        collectKaModuleWithoutExported(kind, to)
        collectExportedDependencies(kind, to)
    }

    private fun ModuleDependency.collectExportedDependencies(kind: KaSourceModuleKind, to: MutableCollection<KaModule>) {
        for (dependency in KotlinExportedDependenciesCollector.getInstance(project).getExportedDependencies(this)) {
            when (dependency) {
                is LibraryDependency -> {
                    to += dependency.library.toKaLibraryModules(project)
                }

                is ModuleDependency -> {
                    dependency.collectKaModuleWithoutExported(kind, to)
                }

                else -> {}
            }
        }
    }

    private fun ModuleDependency.collectKaModuleWithoutExported(kind: KaSourceModuleKind, to: MutableCollection<KaModule>) {
        when (scope) {
            DependencyScope.COMPILE, DependencyScope.PROVIDED -> {
                to.addIfNotNull(module.toKaSourceModule(project, KaSourceModuleKind.PRODUCTION))

                val dependsOnTest = when (kind) {
                    KaSourceModuleKind.PRODUCTION -> productionOnTest
                    KaSourceModuleKind.TEST -> true
                }

                if (dependsOnTest) {
                    to.addIfNotNull(module.toKaSourceModule(project, KaSourceModuleKind.TEST))
                }
            }

            DependencyScope.TEST -> {
                if (kind == KaSourceModuleKind.TEST) {
                    to.addIfNotNull(module.toKaSourceModule(project, KaSourceModuleKind.PRODUCTION))
                    to.addIfNotNull(module.toKaSourceModule(project, KaSourceModuleKind.TEST))
                }
            }

            DependencyScope.RUNTIME -> {}
        }
    }

    private fun KaSourceModuleBase.canDependOn(other: KaModule): Boolean {
        val dependencyFilter = HmppSourceModuleDependencyFilter(targetPlatform)
        return dependencyFilter.isSupportedDependency(other.toDependencyCandidate())
    }

    private fun KaModule.toDependencyCandidate(): SourceModuleDependenciesFilterCandidate {
        if (this is KaLibraryModuleImpl) {
            if (resolvedKotlinLibraries.isNotEmpty()) { // this is a klib-based library
                val isNativeStdlib = resolvedKotlinLibraries.any { kotlinLibrary ->
                    kotlinLibrary.libraryFile.path.endsWith(KONAN_STDLIB_NAME)
                }
                return SourceModuleDependenciesFilterCandidate.KlibLibraryDependency(targetPlatform, isNativeStdlib)
            }
        }
        if (this is KaLibraryModule) { //this is a non-klib-based library
            return SourceModuleDependenciesFilterCandidate.NonKlibLibraryDependency(targetPlatform)
        }
        return SourceModuleDependenciesFilterCandidate.ModuleDependency(targetPlatform)
    }


    companion object {
        fun getInstance(project: Project): KaSourceModuleDependenciesProvider =
            project.service<KaSourceModuleDependenciesProvider>()
    }
}


private class ProductionKeyForModulesWhoseInternalsAreVisible {
    class RegularSourceModule
    class OutsiderSourceModule
}
private object TestKeyForModulesWhoseInternalsAreVisible {
    class RegularSourceModule
    class OutsiderSourceModule
}
