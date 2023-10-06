// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("ModuleEntityUtils")
package com.intellij.workspaceModel.ide.impl.legacyBridge.module

import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar
import com.intellij.platform.workspace.jps.entities.LibraryEntity
import com.intellij.platform.workspace.jps.entities.LibraryTableId
import com.intellij.platform.workspace.jps.entities.ModuleDependencyItem
import com.intellij.platform.workspace.jps.entities.ModuleEntity
import com.intellij.platform.workspace.storage.EntityStorage
import com.intellij.workspaceModel.ide.impl.legacyBridge.module.ModuleManagerBridgeImpl.Companion.moduleMap
import com.intellij.workspaceModel.ide.legacyBridge.ModifiableRootModelBridge
import com.intellij.workspaceModel.ide.legacyBridge.ModuleBridge

/**
 * @return corresponding [com.intellij.openapi.module.Module] or `null` if this entity isn't added to the project model yet.
 */
fun ModuleEntity.findModule(snapshot: EntityStorage): ModuleBridge? {
  return snapshot.moduleMap.getDataByEntity(this)
}

/**
 * Returns all module-level libraries defined in this module.
 */
fun ModuleEntity.getModuleLevelLibraries(snapshot: EntityStorage): Sequence<LibraryEntity> {
  return snapshot.referrers(symbolicId, LibraryEntity::class.java)
}

/**
 * Due to the current project model limitations we don't directly store [com.intellij.platform.workspace.storage.bridgeEntities.SdkEntity],
 * but indirectly [Sdk] can be calculated via [ModuleDependencyItem] related to the module. This method can be used to get [Sdk]
 * if it's assigned to the module as a dependency. [com.intellij.openapi.roots.impl.SdkFinder] and [com.intellij.openapi.projectRoots.ProjectJdkTable]
 * should be used to search for SDKs not bound to any module.
 *
 * @return list of SDKs from module dependencies
 */
fun ModuleEntity.findSdkFromDependencies(): List<Sdk> {
  return this.dependencies.filterIsInstance(ModuleDependencyItem.SdkDependency::class.java).mapNotNull { sdkDependency ->
    ModifiableRootModelBridge.findSdk(sdkDependency.sdkName, sdkDependency.sdkType)
  }
}

/**
 * Due to the current project model limitations we don't directly store global libraries, but indirectly they can be calculated
 * via [ModuleDependencyItem] related to the module. This method can be used to get [Library] if it's assigned to the module as
 * a dependency. [com.intellij.openapi.roots.impl.libraries.ApplicationLibraryTable] should be used to search for global libraries
 * not bound to any module.
 *
 * @return list of global libraries from module dependencies
 */
fun ModuleEntity.findGlobalLibsFromDependencies(project: Project): List<Library> {
  return this.dependencies.filterIsInstance(ModuleDependencyItem.Exportable.LibraryDependency::class.java).mapNotNull { libraryDependency ->
    val libraryId = libraryDependency.library
    if (libraryId.tableId !is LibraryTableId.GlobalLibraryTableId) return@mapNotNull null
    LibraryTablesRegistrar.getInstance().getLibraryTableByLevel(libraryId.tableId.level, project)?.getLibraryByName(libraryId.name)
  }
}