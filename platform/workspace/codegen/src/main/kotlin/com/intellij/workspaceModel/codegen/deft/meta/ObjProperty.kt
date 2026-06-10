// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.codegen.deft.meta

interface TypeProperty<V> {
  val name: String

  val valueType: ValueType<V>
}

interface Annotated {
  val annotations: List<ObjAnnotation>
}

/**
 * Same as [kotlin.reflect.KProperty]
 */
interface ObjProperty<T : Obj, V> : Obj, TypeProperty<V> {
  val suspend: Boolean

  val open: Boolean

  val mutable: Boolean

  val receiver: ObjClass<T>

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
  val constructorParameter: Boolean

  val classLocalId: Int

  @Deprecated("Look for the @EqualsBy in annotations instead", replaceWith = ReplaceWith("annotations"))
  val isKey: Boolean
    get() = false
}

/**
 * Same as [kotlin.reflect.KProperty1]
 */
interface ExtProperty<T : Obj, V> : ObjProperty<T, V>, Annotated {
  val module: ObjModule

  val moduleLocalId: Int
}