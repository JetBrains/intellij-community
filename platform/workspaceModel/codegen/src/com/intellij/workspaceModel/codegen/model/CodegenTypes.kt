package org.jetbrains.deft.codegen.model

import deft.storage.codegen.suspendable
import org.jetbrains.deft.impl.ObjModule
import org.jetbrains.deft.impl.fields.MemberOrExtField
import org.jetbrains.deft.obj.impl.fields.MetaTypes

object CodegenTypes : ObjModule(Id("org.jetbrains.deft.Codegen")) {
  @InitApi
  override fun init() {
    requireDependency(MetaTypes)

    beginExtFieldsInit(1)
    registerExtField(MemberOrExtField.suspendable)
  }
}