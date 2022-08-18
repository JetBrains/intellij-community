package com.intellij.workspaceModel.codegen.deft

import org.jetbrains.deft.Obj

class ExtField<P : Obj, V>(
  owner: ObjType<P, *>,
  name: String,
  type: ValueType<V>
) : MemberOrExtField<P, V>(owner, name, type)