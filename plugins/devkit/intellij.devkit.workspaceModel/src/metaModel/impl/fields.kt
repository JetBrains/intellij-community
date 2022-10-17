// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.workspaceModel.metaModel.impl

import com.intellij.devkit.workspaceModel.metaModel.ObjMetaElementWithSource
import com.intellij.workspaceModel.codegen.deft.meta.*
import org.jetbrains.kotlin.descriptors.SourceElement

private val fieldObjType = ObjTypeImpl<ObjProperty<*, *>>()

sealed class ObjPropertyBase<T : Obj, V>(
  override val receiver: ObjClass<T>,
  override val name: String,
  override val valueType: ValueType<V>,
  override val valueKind: ObjProperty.ValueKind,
  override val open: Boolean,
  override val content: Boolean,
  override val sourceElement: SourceElement
) : ObjProperty<T, V>, ObjMetaElementWithSource {
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
  sourceElement: SourceElement
) : ObjPropertyBase<T, V>(receiver, name, valueType, valueKind, open, content, sourceElement), OwnProperty<T, V> {

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
  override val moduleLocalId: Int,
  sourceElement: SourceElement
) : ObjPropertyBase<T, V>(receiver, name, valueType, valueKind, open, content, sourceElement), ExtProperty<T, V> {
  override fun toString(): String = "$name (${receiver.name})"
}