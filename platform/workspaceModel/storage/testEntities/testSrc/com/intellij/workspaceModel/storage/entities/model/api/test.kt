// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.storage.entities.model.api

import com.intellij.workspaceModel.storage.WorkspaceEntity
import com.intellij.workspaceModel.storage.bridgeEntities.ModuleDependencyItem
import org.jetbrains.deft.annotations.Child


interface FooEntity: WorkspaceEntity {
  val name: String
  val moduleDependency: ModuleDependencyItem.Exportable.ModuleDependency

}


interface AnotherTest: WorkspaceEntity {
  val name: String
  val testField: TestEntity
}

val TestEntity.anotherTest: @Child AnotherTest?
    by WorkspaceEntity.extension()