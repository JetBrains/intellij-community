// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.storage.entities.test.api

import com.intellij.workspaceModel.storage.*
import org.jetbrains.deft.ObjBuilder
import org.jetbrains.deft.Type
import org.jetbrains.deft.annotations.Child
import com.intellij.workspaceModel.storage.EntitySource
import com.intellij.workspaceModel.storage.GeneratedCodeApiVersion
import com.intellij.workspaceModel.storage.ModifiableWorkspaceEntity
import com.intellij.workspaceModel.storage.MutableEntityStorage
import com.intellij.workspaceModel.storage.impl.ExtRefKey
import com.intellij.workspaceModel.storage.impl.updateOneToManyChildrenOfParent
import com.intellij.workspaceModel.storage.referrersx




interface SelfLinkedEntity : WorkspaceEntity {
  val parentEntity: SelfLinkedEntity?

  //region generated code
  //@formatter:off
  @GeneratedCodeApiVersion(0)
  interface Builder: SelfLinkedEntity, ModifiableWorkspaceEntity<SelfLinkedEntity>, ObjBuilder<SelfLinkedEntity> {
      override var parentEntity: SelfLinkedEntity?
      override var entitySource: EntitySource
  }
  
  companion object: Type<SelfLinkedEntity, Builder>() {
      operator fun invoke(entitySource: EntitySource, init: (Builder.() -> Unit)? = null): SelfLinkedEntity {
          val builder = builder()
          builder.entitySource = entitySource
          init?.invoke(builder)
          return builder
      }
  }
  //@formatter:on
  //endregion

}
//region generated code
fun MutableEntityStorage.modifyEntity(entity: SelfLinkedEntity, modification: SelfLinkedEntity.Builder.() -> Unit) = modifyEntity(SelfLinkedEntity.Builder::class.java, entity, modification)
var SelfLinkedEntity.Builder.children: @Child List<SelfLinkedEntity>
    get() {
        return referrersx(SelfLinkedEntity::parentEntity)
    }
    set(value) {
        val diff = (this as SelfLinkedEntityImpl.Builder).diff
        if (diff != null) {
            for (item in value) {
                if ((item as SelfLinkedEntityImpl.Builder).diff == null) {
                    item._parentEntity = this
                    diff.addEntity(item)
                }
            }
            diff.updateOneToManyChildrenOfParent(SelfLinkedEntityImpl.PARENTENTITY_CONNECTION_ID, this, value)
        }
        else {
            val key = ExtRefKey("SelfLinkedEntity", "parentEntity", true, SelfLinkedEntityImpl.PARENTENTITY_CONNECTION_ID)
            this.extReferences[key] = value
            
            for (item in value) {
                (item as SelfLinkedEntityImpl.Builder)._parentEntity = this
            }
        }
    }

//endregion

val SelfLinkedEntity.children: List<@Child SelfLinkedEntity>
  get() = referrersx(SelfLinkedEntity::parentEntity)
