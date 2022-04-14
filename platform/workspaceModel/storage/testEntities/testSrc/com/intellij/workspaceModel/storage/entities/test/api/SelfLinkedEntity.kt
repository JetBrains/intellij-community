// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.storage.entities.test.api

import com.intellij.workspaceModel.storage.WorkspaceEntity
import com.intellij.workspaceModel.storage.referrersx
import org.jetbrains.deft.annotations.Child
import com.intellij.workspaceModel.storage.EntitySource
import com.intellij.workspaceModel.storage.ModifiableWorkspaceEntity
import org.jetbrains.deft.ObjBuilder
import org.jetbrains.deft.Type


interface SelfLinkedEntity : WorkspaceEntity {
  val parentEntity: SelfLinkedEntity?
  //region generated code
  //@formatter:off
  interface Builder: SelfLinkedEntity, ModifiableWorkspaceEntity<SelfLinkedEntity>, ObjBuilder<SelfLinkedEntity> {
      override var parentEntity: SelfLinkedEntity?
      override var entitySource: EntitySource
  }
  
  companion object: Type<SelfLinkedEntity, Builder>()
  //@formatter:on
  //endregion

}

val SelfLinkedEntity.children: List<@Child SelfLinkedEntity>
  get() = referrersx(SelfLinkedEntity::parentEntity)
