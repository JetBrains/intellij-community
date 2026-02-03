// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.codegen.impl.writer.extensions

import com.intellij.workspaceModel.codegen.deft.meta.ObjProperty
import com.intellij.workspaceModel.codegen.deft.meta.TypeProperty
import com.intellij.workspaceModel.codegen.deft.meta.ValueType

internal val ValueType.JvmClass<*>.kotlinClassName: String
  get() = javaClassName.replace('$','.')

internal fun ValueType<*>.isEntityRef(property: TypeProperty<*>): Boolean {
  return isRefType() && property is ObjProperty<*, *> && !property.isComputable && !property.withDefault
}


internal val ValueType.AbstractClass<*>.allFinalSubClasses: List<ValueType.JvmClass<*>>
  get() = allFinalSubClassesByFqn.values.toList()

// Function is used to get unique final subclasses
private val ValueType.AbstractClass<*>.allFinalSubClassesByFqn: Map<String, ValueType.JvmClass<*>>
  get() {
    val finalClassesByFqn: MutableMap<String, ValueType.JvmClass<*>> = hashMapOf()
    subclasses.forEach {
      if (it is ValueType.AbstractClass<*>) {
        finalClassesByFqn.putAll(it.allFinalSubClassesByFqn)
      } else {
        finalClassesByFqn[it.javaClassName] = it
      }
    }
    return finalClassesByFqn
  }