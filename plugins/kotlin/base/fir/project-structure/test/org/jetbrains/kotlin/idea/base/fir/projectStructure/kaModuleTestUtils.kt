// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.fir.projectStructure

import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.modules
import com.intellij.openapi.roots.OrderEnumerator
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar
import org.jetbrains.kotlin.analysis.api.projectStructure.KaModule
import org.jetbrains.kotlin.analysis.api.projectStructure.allDirectDependencies
import org.jetbrains.kotlin.idea.base.projectStructure.KaSourceModuleKind
import org.jetbrains.kotlin.idea.base.projectStructure.moduleInfo.allSdks
import org.jetbrains.kotlin.idea.base.projectStructure.toKaLibraryModule
import org.jetbrains.kotlin.idea.base.projectStructure.toKaLibraryModules
import org.jetbrains.kotlin.idea.base.projectStructure.toKaSourceModule


internal fun Project.getAllKaModules(): List<KaModule> = buildSet {
    addAll(getAllKaModules(modules.toList()))

    val libraryTablesRegistrar = LibraryTablesRegistrar.getInstance()
    addAll(getAllKaModules(libraryTablesRegistrar.libraryTable.libraries.toList()))
    addAll(getAllKaModules(libraryTablesRegistrar.getLibraryTable(this@getAllKaModules).libraries.toList()))
    modules.forEach { module ->
        OrderEnumerator.orderEntries(module).forEachLibrary { l ->
            addAll(l.toKaLibraryModules(this@getAllKaModules))
        }
    }
    addAll(allSdks(modules).map { it.toKaLibraryModule(this@getAllKaModules) })
}.sortedWith(kaModulesComparatorForStableRendering)


internal val kaModulesComparatorForStableRendering =
    compareBy<KaModule> { it.getKaModuleClass() }
        .thenBy { it.getOneLineModuleDescriptionForRendering() }
        .thenBy { it.targetPlatform.getTargetPlatformDescriptionForRendering() }

internal fun Collection<KaModule>.computeDependenciesClosure(): List<KaModule> {
    val result = mutableSetOf<KaModule>()

    fun visit(module: KaModule) {
        if (module in result) return
        result += module
        module.allDirectDependencies().forEach(::visit)
    }

    forEach(::visit)

    return result.toList()
}

internal fun getAllKaModules(modules: Collection<Module>): List<KaModule> =
    modules.flatMap {
        listOfNotNull(
            it.toKaSourceModule(KaSourceModuleKind.PRODUCTION),
            it.toKaSourceModule(KaSourceModuleKind.TEST),
        )
    }

internal fun Project.getAllKaModules(libraries: Collection<Library>): List<KaModule> =
    libraries.flatMap { it.toKaLibraryModules(this) }
