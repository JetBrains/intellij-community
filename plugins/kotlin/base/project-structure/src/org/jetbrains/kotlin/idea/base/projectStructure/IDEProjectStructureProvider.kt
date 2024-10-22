// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.projectStructure

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.roots.libraries.Library as OpenapiLibrary
import com.intellij.openapi.projectRoots.Sdk as OpenapiSdk
import com.intellij.platform.workspace.jps.entities.LibraryId
import com.intellij.platform.workspace.jps.entities.ModuleId
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.analysis.api.platform.projectStructure.KotlinProjectStructureProvider
import org.jetbrains.kotlin.analysis.api.platform.projectStructure.KotlinProjectStructureProviderBase
import org.jetbrains.kotlin.analysis.api.projectStructure.KaLibraryModule
import org.jetbrains.kotlin.analysis.api.projectStructure.KaModule
import org.jetbrains.kotlin.analysis.api.projectStructure.KaSourceModule
import com.intellij.openapi.module.Module as OpenapiModule

@ApiStatus.Internal
abstract class IDEProjectStructureProvider : KotlinProjectStructureProviderBase() {
    abstract fun getKaSourceModule(moduleId: ModuleId, type: KaSourceModuleKind): KaSourceModule?

    abstract fun getKaSourceModuleKind(module: KaSourceModule): KaSourceModuleKind

    abstract fun getKaSourceModuleSymbolId(module: KaSourceModule): ModuleId

    abstract fun getKaSourceModule(openapiModule: OpenapiModule, type: KaSourceModuleKind): KaSourceModule?

    abstract fun getOpenapiModule(module: KaSourceModule): OpenapiModule

    abstract fun getKaLibraryModules(libraryId: LibraryId): List<KaLibraryModule>

    abstract fun getKaLibraryModules(library: OpenapiLibrary): List<KaLibraryModule>

    abstract fun getKaLibraryModuleSymbolicId(libraryModule: KaLibraryModule): LibraryId

    abstract fun getOpenapiLibrary(module: KaLibraryModule): OpenapiLibrary?

    abstract fun getOpenapiSdk(module: KaLibraryModule): OpenapiSdk?

    abstract fun getContainingKaModules(virtualFile: VirtualFile): List<KaModule>
}


internal val Project.ideProjectStructureProvider: IDEProjectStructureProvider
    get() = KotlinProjectStructureProvider.getInstance(this) as IDEProjectStructureProvider