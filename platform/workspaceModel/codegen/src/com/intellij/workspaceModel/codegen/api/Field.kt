package com.intellij.workspaceModel.codegen.deft

import org.jetbrains.deft.Obj

/**
 * non extension field
 */
class Field<T : Obj, V>(
  owner: ObjType<T, *>,
  name: String,
  type: ValueType<V>
) : MemberOrExtField<T, V>(owner, name, type) {

  init {
    owner.structure.addField(this)
  }

  var base: Field<*, *>? = null

  enum class Default {
    none, plain, suspend
  }
}