package com.intellij.workspaceModel.codegen.deft

import org.jetbrains.deft.Obj
import com.intellij.workspaceModel.codegen.deft.model.DefField
import com.intellij.workspaceModel.codegen.deft.ObjType
import com.intellij.workspaceModel.codegen.deft.ValueType

abstract class MemberOrExtField<P : Obj, V>(
  val owner: ObjType<P, *>,
  val name: String,
  val type: ValueType<V>
) : Obj {
  override fun toString(): String = "${owner.name}.$name"

  var open: Boolean = false
  var exDef: DefField? = null
  var content: Boolean = false
  var constructorField = false
  var hasDefault: Field.Default = Field.Default.none
  var defaultValue: String? = null
  var ignored: Boolean = false

  val hasSetter: Boolean
    get() =
      if (open) true
      else hasDefault == Field.Default.none // final fields: setter allowed only for fields without default value
}