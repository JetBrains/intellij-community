package org.jetbrains.deft.impl.fields

import org.jetbrains.deft.Obj
import org.jetbrains.deft.impl.ObjModule
import org.jetbrains.deft.impl.ObjType
import org.jetbrains.deft.impl.ValueType

/**
 * non extension field
 */
class Field<T : Obj, V>(
  owner: ObjType<T, *>,
  val id: Int,
  name: String,
  type: ValueType<V>
) : MemberOrExtField<T, V>(owner, name, type) {
  data class Id(val module: ObjModule.Id, val typeId: Int, val fieldId: Int)

  init {
    owner.structure.addField(this)
  }

  var base: Field<*, *>? = null

  enum class Default {
    none, plain, suspend
  }
}