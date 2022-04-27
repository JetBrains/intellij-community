package org.jetbrains.deft.impl.fields

import org.jetbrains.deft.Obj
import org.jetbrains.deft.codegen.model.DefField
import org.jetbrains.deft.impl.ExtensibleImpl
import org.jetbrains.deft.impl.ObjType
import org.jetbrains.deft.impl.ValueType

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
  var exDef: DefField?
    get() = unsafeGetExtension()
    set(value) {
      unsafeAddExtension(value!!)
    }

  val hasSetter: Boolean
    get() =
      if (open) true
      else hasDefault == Field.Default.none // final fields: setter allowed only for fields without default value
}