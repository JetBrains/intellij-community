// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.jps.entities

import com.intellij.platform.workspace.storage.EntityStorage
import com.intellij.platform.workspace.storage.referrers

val ModuleEntity.sourceRoots: List<SourceRootEntity>
  get() = contentRoots.flatMap { it.sourceRoots }


fun ModuleEntity.getModuleLibraries(storage: EntityStorage): Sequence<LibraryEntity> {
  return storage.entities(LibraryEntity::class.java)
    .filter { (it.symbolicId.tableId as? LibraryTableId.ModuleLibraryTableId)?.moduleId?.name == name }
}

val EntityStorage.projectLibraries: Sequence<LibraryEntity>
  get() = entities(LibraryEntity::class.java).filter { it.symbolicId.tableId == LibraryTableId.ProjectLibraryTableId }

fun ModuleEntity.collectTransitivelyDependentModules(storage: EntityStorage): Set<ModuleEntity> {
  val result = mutableSetOf(this)
  
  fun collectDependentModulesRecursively(module: ModuleEntity) {
    for (referrer in storage.referrers<ModuleEntity>(module.symbolicId)) {
      val dependency = referrer.dependencies.filterIsInstance<ModuleDependency>().find { it.module == module.symbolicId } ?: continue
      val added = result.add(referrer)
      if (added && dependency.exported) {
        collectDependentModulesRecursively(referrer)
      }
    }
  }
  
  collectDependentModulesRecursively(this)
  return result
}
