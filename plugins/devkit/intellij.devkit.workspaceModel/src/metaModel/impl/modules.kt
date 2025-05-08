// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.workspaceModel.metaModel.impl

import com.intellij.workspaceModel.codegen.deft.meta.*

val objModuleType: ObjType<ObjModule> = ObjTypeImpl()

open class ObjModuleImpl(override val name: String) : ObjModule {
  private val mutableDependencies: MutableList<ObjModule> = ArrayList()
  private val mutableTypes: MutableList<ObjClass<*>> = ArrayList()
  private val mutableAbstractTypes: MutableList<ValueType.AbstractClass<*>> = ArrayList()
  private val mutableExtensions: MutableList<ExtProperty<*, *>> = ArrayList()

  override val objType: ObjType<*>
    get() = objModuleType

  override val dependencies: List<ObjModule>
    get() = mutableDependencies

  override val types: List<ObjClass<*>>
    get() = mutableTypes

  override val abstractTypes: List<ValueType.AbstractClass<*>>
    get() = mutableAbstractTypes

  fun addType(objType: ObjClass<*>) {
    mutableTypes.add(objType)
    mutableTypes.sortBy { it.name }
  }

  fun addAbstractType(abstractType: ValueType.AbstractClass<*>) {
    mutableAbstractTypes.add(abstractType)
  }

  fun addExtension(ext: ExtProperty<*, *>) {
    mutableExtensions.add(ext)
  }

  override val extensions: List<ExtProperty<*, *>>
    get() = mutableExtensions

  override fun toString(): String = name
}

class CompiledObjModuleImpl(name: String) : ObjModuleImpl(name), CompiledObjModule {
  override fun objClass(typeId: Int): ObjClass<*> = types[typeId]

  override fun <T : Obj, V> extField(
    receiver: ObjClass<*>,
    name: String,
    default: T.() -> V
  ): ExtProperty<T, V> {
    @Suppress("UNCHECKED_CAST")
    return extensions.first { it.name == name } as ExtProperty<T, V>
  }

  override val objType: ObjType<*>
    get() = objModuleType
}