package com.intellij.workspaceModel.codegen.deft

import org.jetbrains.deft.*
import com.intellij.workspaceModel.codegen.deft.ObjType
import com.intellij.workspaceModel.codegen.deft.ValueType
import kotlin.reflect.KProperty

class ExtField<P : Obj, V>(
  val id: ExtFieldId,
  owner: ObjType<P, *>,
  name: String,
  type: ValueType<V>
) : MemberOrExtField<P, V>(owner, name, type)