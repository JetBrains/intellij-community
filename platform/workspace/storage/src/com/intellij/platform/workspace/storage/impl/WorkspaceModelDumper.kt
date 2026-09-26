// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.impl

import com.intellij.platform.workspace.storage.EntityStorage

@Suppress("unused")
public object WorkspaceModelDumper {
  public fun simpleEntities(store: EntityStorage): String {
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