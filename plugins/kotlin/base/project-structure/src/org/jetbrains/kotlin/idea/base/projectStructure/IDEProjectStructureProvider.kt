// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.projectStructure

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.ModificationTracker
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.workspace.jps.entities.LibraryEntity
import com.intellij.platform.workspace.jps.entities.LibraryId
import com.intellij.platform.workspace.jps.entities.ModuleEntity
import com.intellij.platform.workspace.jps.entities.ModuleId
import com.intellij.platform.workspace.storage.SymbolicEntityId
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.WorkspaceEntityWithSymbolicId
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.analysis.api.platform.projectStructure.KotlinProjectStructureProvider
import org.jetbrains.kotlin.analysis.api.platform.projectStructure.KotlinProjectStructureProviderBase
import org.jetbrains.kotlin.analysis.api.projectStructure.KaLibraryModule
import org.jetbrains.kotlin.analysis.api.projectStructure.KaModule
import org.jetbrains.kotlin.analysis.api.projectStructure.KaScriptModule
import org.jetbrains.kotlin.analysis.api.projectStructure.KaSourceModule
import org.jetbrains.kotlin.library.KotlinLibrary
import com.intellij.openapi.module.Module as OpenapiModule
import com.intellij.openapi.projectRoots.Sdk as OpenapiSdk
import com.intellij.openapi.roots.libraries.Library as OpenapiLibrary

@ApiStatus.Internal
abstract class IDEProjectStructureProvider : KotlinProjectStructureProviderBase() {
    /**
     * Needed for [org.jetbrains.kotlin.idea.base.fir.projectStructure.DelegatingIDEProjectStructureProvider] to know the real provider.
     *
     * It's a temporary variable needed until we have [DelegatingIDEProjectStructureProvider]
     */
    abstract val self: IDEProjectStructureProvider

    abstract fun getKaSourceModule(moduleId: ModuleId, kind: KaSourceModuleKind): KaSourceModule?

    abstract fun getKaSourceModules(moduleId: ModuleId): List<KaSourceModule>

    abstract fun getKaSourceModule(moduleEntity: ModuleEntity, kind: KaSourceModuleKind): KaSourceModule?

    abstract fun getKaSourceModules(moduleEntity: ModuleEntity): List<KaSourceModule>

    abstract fun getKaSourceModuleSymbolId(module: KaSourceModule): ModuleId

    abstract fun getKaSourceModule(openapiModule: OpenapiModule, type: KaSourceModuleKind): KaSourceModule?

    abstract fun getKaSourceModules(openapiModule: OpenapiModule): List<KaSourceModule>

    abstract fun getOpenapiModule(module: KaSourceModule): OpenapiModule

    abstract fun getKaLibraryModules(libraryId: LibraryId): List<KaLibraryModule>

    abstract fun getKaLibraryModules(libraryEntity: LibraryEntity): List<KaLibraryModule>

    open fun getKaScriptLibraryModules(libraryEntity: WorkspaceEntity): List<KaLibraryModule> = listOf()

    open fun getKaScriptModules(scriptEntity: WorkspaceEntity): List<KaScriptModule> = listOf()

    abstract fun getKaLibraryModules(library: OpenapiLibrary): List<KaLibraryModule>

    abstract fun getKaLibraryModuleSymbolicId(libraryModule: KaLibraryModule): LibraryId

    abstract fun getOpenapiLibrary(module: KaLibraryModule): OpenapiLibrary?

    abstract fun getOpenapiSdk(module: KaLibraryModule): OpenapiSdk?

    abstract fun getKaLibraryModule(sdk: OpenapiSdk): KaLibraryModule

    abstract fun getKotlinLibraries(module: KaLibraryModule): List<KotlinLibrary>

    abstract fun getAssociatedKaModules(virtualFile: VirtualFile): List<KaModule>

    /**
     * Return tracker which will be incremented when any `KaModule` is invalidated.
     */
    abstract fun getCacheDependenciesTracker(): ModificationTracker
}


@get:ApiStatus.Internal
val Project.ideProjectStructureProvider: IDEProjectStructureProvider
    get() = KotlinProjectStructureProvider.getInstance(this) as IDEProjectStructureProvider
