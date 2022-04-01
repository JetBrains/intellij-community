package com.intellij.workspaceModel.storage

import com.intellij.workspaceModel.storage.impl.*
import com.intellij.workspaceModel.storage.EntitySource
import org.jetbrains.deft.IntellijWsTestIj.IntellijWsTestIj
import org.jetbrains.deft.Obj
import org.jetbrains.deft.ObjBuilder
import org.jetbrains.deft.impl.*
import org.jetbrains.deft.impl.fields.*


// ------------------- Entity with consistency assertion --------------------------------

interface AssertConsistencyEntity : WorkspaceEntity {
  val passCheck: Boolean
  //region generated code
  //@formatter:off
  interface Builder: AssertConsistencyEntity, ObjBuilder<AssertConsistencyEntity> {
      override var passCheck: Boolean
      override var entitySource: EntitySource
  }
  
  companion object: ObjType<AssertConsistencyEntity, Builder>(IntellijWsTestIj, 39) {
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
