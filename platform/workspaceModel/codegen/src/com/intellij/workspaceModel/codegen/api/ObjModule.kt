package com.intellij.workspaceModel.codegen.deft

abstract class ObjModule {
  internal lateinit var byId: Array<ObjType<*, *>?>

  val extFields = mutableListOf<ExtField<*, *>>()

  internal fun typeIndex(id: Int) = id - 1
}

