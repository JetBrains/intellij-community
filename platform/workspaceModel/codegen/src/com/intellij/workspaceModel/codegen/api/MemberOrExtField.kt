package org.jetbrains.deft.impl.fields

import org.jetbrains.deft.Obj
import org.jetbrains.deft.ObjBuilder
import org.jetbrains.deft.impl.ExtensibleImpl
import org.jetbrains.deft.impl.ObjType
import org.jetbrains.deft.impl.ValueType
import org.jetbrains.deft.obj.impl.fields.MetaTypes

abstract class MemberOrExtField<P : Obj, V>(
  val owner: ObjType<P, *>,
  val name: String,
  val type: ValueType<V>
) : ExtensibleImpl() {
  override fun toString(): String = "${owner.name}.$name"

  var open: Boolean = false
  var content: Boolean = false
  var constructorField = false
  var hasDefault: Field.Default = Field.Default.none
  var defaultValue: String? = null

  val hasSetter: Boolean
    get() =
      if (open) true
      else hasDefault == Field.Default.none // final fields: setter allowed only for fields without default value

  companion object : ObjType<MemberOrExtField<*, *>, ObjBuilder<MemberOrExtField<*, *>>>(MetaTypes, 0)
}