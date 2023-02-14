// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.codegen.deft.meta

import com.intellij.workspaceModel.codegen.deft.annotations.Name
import com.intellij.workspaceModel.codegen.deft.annotations.Parent

/**
 * Same as [kotlin.reflect.KProperty]
 */
interface ObjProperty<T : Obj, V> : Obj {
  val suspend: Boolean

  val open: Boolean

  val receiver: ObjClass<T>

  @Name
  val name: String

  val valueType: ValueType<V>

  val valueKind: ValueKind

  sealed interface ValueKind {
    object Plain : ValueKind
    class Computable(val expression: String) : ValueKind
    class WithDefault(val value: String) : ValueKind
  }

  ////// Kotlin DSL

  val content: Boolean
}

/**
 * Same as [kotlin.reflect.KProperty1]
 */
interface OwnProperty<T : Obj, V> : ObjProperty<T, V> {
  @Parent
  override val receiver: ObjClass<T>

  val constructorParameter: Boolean

  val classLocalId: Int

  val isKey: Boolean
}

/**
 * Same as [kotlin.reflect.KProperty1]
 */
interface ExtProperty<T : Obj, V> : ObjProperty<T, V> {
  @Parent
  val module: ObjModule

  override val receiver: ObjClass<T>

  val moduleLocalId: Int
}