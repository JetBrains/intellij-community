// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.codegen.deft.meta.impl

import com.intellij.workspaceModel.codegen.deft.meta.*

private val fieldObjType = ObjTypeImpl<ObjProperty<*, *>>()

sealed class ObjPropertyBase<T : Obj, V>(
  override val receiver: ObjClass<T>,
  override val name: String,
  override val valueType: ValueType<V>,
  override val valueKind: ObjProperty.ValueKind,
  override val open: Boolean,
  override val content: Boolean
) : ObjProperty<T, V> {
  private val mutableOverrides: MutableList<ObjProperty<T, V>> = ArrayList()
  private val mutableOverriddenBy: MutableList<ObjProperty<T, V>> = ArrayList()

  override val suspend: Boolean
    get() = false


  override val objType: ObjType<*>
    get() = fieldObjType
}

class OwnPropertyImpl<T : Obj, V>(
  receiver: ObjClass<T>,
  name: String,
  valueType: ValueType<V>,
  valueKind: ObjProperty.ValueKind,
  open: Boolean,
  content: Boolean,
  override val constructorParameter: Boolean,
  override val classLocalId: Int,
  override val isKey: Boolean,
) : ObjPropertyBase<T, V>(receiver, name, valueType, valueKind, open, content), OwnProperty<T, V> {

  override fun toString(): String = "$name (${receiver.name})"
}

class ExtPropertyImpl<T : Obj, V>(
  receiver: ObjClass<T>,
  name: String,
  valueType: ValueType<V>,
  valueKind: ObjProperty.ValueKind,
  open: Boolean,
  content: Boolean,
  override val module: ObjModule,
  override val moduleLocalId: Int
) : ObjPropertyBase<T, V>(receiver, name, valueType, valueKind, open, content), ExtProperty<T, V> {
  override fun toString(): String = "$name (${receiver.name})"
}