// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.storage.entities.model.api

import com.intellij.workspaceModel.storage.WorkspaceEntity
import com.intellij.workspaceModel.storage.entity.TestEntity
import com.intellij.workspaceModel.storage.EntitySource
import com.intellij.workspaceModel.storage.ModifiableWorkspaceEntity
import org.jetbrains.deft.ObjBuilder
import org.jetbrains.deft.Type



interface AnotherTest: WorkspaceEntity {
  val name: String
  val testField: TestEntity
  //region generated code
  //@formatter:off
  interface Builder: AnotherTest, ModifiableWorkspaceEntity<AnotherTest>, ObjBuilder<AnotherTest> {
      override var name: String
      override var entitySource: EntitySource
  }
  
  companion object: Type<AnotherTest, Builder>()
  //@formatter:on
  //endregion

}