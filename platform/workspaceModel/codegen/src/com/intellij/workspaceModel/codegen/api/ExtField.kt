package org.jetbrains.deft.impl.fields

import org.jetbrains.deft.*
import org.jetbrains.deft.impl.ObjType
import org.jetbrains.deft.impl.ValueType
import kotlin.reflect.KProperty

class ExtField<P : Obj, V>(
  val id: ExtFieldId,
  owner: ObjType<P, *>,
  name: String,
  type: ValueType<V>
) : MemberOrExtField<P, V>(owner, name, type)