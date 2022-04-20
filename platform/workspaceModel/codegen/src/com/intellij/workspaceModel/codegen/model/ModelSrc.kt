package org.jetbrains.deft.codegen.model

import org.jetbrains.deft.getExtension
import org.jetbrains.deft.impl.TBlob
import org.jetbrains.deft.impl.fields.ExtField
import org.jetbrains.deft.impl.fields.MemberOrExtField

val MemberOrExtField.Companion.def: ExtField<MemberOrExtField<*, *>, DefField>
  by CodegenTypes.defExt(2, MemberOrExtField, TBlob<DefField>("DefField"))

var MemberOrExtField<*, *>.def: DefField?
  get() = getExtension(MemberOrExtField.def)
  set(value) {
    unsafeAddExtension(MemberOrExtField.def, value!!)
  }