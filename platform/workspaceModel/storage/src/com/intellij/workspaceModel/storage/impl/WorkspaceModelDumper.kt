// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspaceModel.storage.impl

import com.intellij.workspaceModel.storage.WorkspaceEntityStorage

@Suppress("unused")
object WorkspaceModelDumper {
  fun simpleEntities(store: WorkspaceEntityStorage): String {
    return buildString {
      store as AbstractEntityStorage
      val classToEntities = HashMap<String, String>()
      store.entitiesByType.entityFamilies.forEachIndexed { i, entities ->
        if (entities == null) return@forEachIndexed
        val entityClass = i.findWorkspaceEntity()

        val ent = buildString {
          entities.entities.filterNotNull().forEach {
            this.append("  - ${it.javaClass.simpleName}:${it.id}\n")
          }
        }
        classToEntities[entityClass.simpleName] = ent
      }

      classToEntities.keys.sorted().forEach { key ->
        this.append("$key:\n")
        this.append("${classToEntities[key]}\n")
      }
    }
  }
}