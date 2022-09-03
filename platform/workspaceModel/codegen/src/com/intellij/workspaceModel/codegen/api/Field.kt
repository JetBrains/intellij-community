package com.intellij.workspaceModel.codegen.deft

import org.jetbrains.deft.Obj
import com.intellij.workspaceModel.codegen.deft.ObjType
import com.intellij.workspaceModel.codegen.deft.ValueType

/**
 * non extension field
 */
class Field<T : Obj, V>(
  owner: ObjType<T, *>,
  val id: Int,
  name: String,
  type: ValueType<V>
) : MemberOrExtField<T, V>(owner, name, type) {
  data class Id(val typeId: Int, val fieldId: Int)

  init {
    owner.structure.addField(this)
  }

  var base: Field<*, *>? = null

  enum class Default {
    none, plain, suspend
  }
}