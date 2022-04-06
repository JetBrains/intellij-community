package com.intellij.workspaceModel.storage.entities.api

import com.intellij.workspaceModel.storage.EntitySource
import com.intellij.workspaceModel.storage.WorkspaceEntity
import com.intellij.workspaceModel.storage.WorkspaceEntityStorageBuilder
import org.jetbrains.deft.IntellijWs.testEntities.TestEntities
import org.jetbrains.deft.ObjBuilder
import org.jetbrains.deft.impl.ObjType
import org.jetbrains.deft.impl.TBlob
import org.jetbrains.deft.impl.TBoolean
import org.jetbrains.deft.impl.fields.Field



// ------------------- Entity with consistency assertion --------------------------------

interface AssertConsistencyEntity : WorkspaceEntity {
  val passCheck: Boolean


  //region generated code
  //@formatter:off
  interface Builder: AssertConsistencyEntity, ObjBuilder<AssertConsistencyEntity> {
      override var passCheck: Boolean
      override var entitySource: EntitySource
  }
  
  companion object: ObjType<AssertConsistencyEntity, Builder>(TestEntities, 26) {
      val passCheck: Field<AssertConsistencyEntity, Boolean> = Field(this, 0, "passCheck", TBoolean)
      val entitySource: Field<AssertConsistencyEntity, EntitySource> = Field(this, 0, "entitySource", TBlob("EntitySource"))
  }
  //@formatter:on
  //endregion

}

fun WorkspaceEntityStorageBuilder.addAssertConsistencyEntity(passCheck: Boolean, source: EntitySource = MySource): AssertConsistencyEntity {
  val assertConsistencyEntity = AssertConsistencyEntity {
    this.passCheck = passCheck
    this.entitySource = source
  }
  this.addEntity(assertConsistencyEntity)
  return assertConsistencyEntity
}
