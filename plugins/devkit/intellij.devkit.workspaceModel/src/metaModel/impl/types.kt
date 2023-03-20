// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.workspaceModel.metaModel.impl

import com.intellij.devkit.workspaceModel.metaModel.ObjMetaElementWithSource
import com.intellij.workspaceModel.codegen.deft.meta.*
import org.jetbrains.kotlin.descriptors.SourceElement

object ObjTypeType : ObjType<ObjType<*>> {
  override val classifier: ObjType<ObjType<*>>
    get() = this
  override val arguments: List<ObjType<*>>
    get() = emptyList()
  override val objType: ObjType<*>
    get() = this
}

open class ObjTypeImpl<T> : ObjType<T> {
  override val classifier: ObjType<T>
    get() = this
  override val arguments: List<ObjType<*>>
    get() = emptyList()
  override val objType: ObjType<*>
    get() = ObjTypeType
}

class ObjClassImpl<T : Obj>(
  override val module: ObjModule,
  override val name: String,
  override val openness: ObjClass.Openness,
  override val sourceElement: SourceElement
) : ObjClass<T>, ObjTypeImpl<T>(), ObjMetaElementWithSource {
  override var parentField: OwnProperty<T, *>? = null
  override var nameField: OwnProperty<T, *>? = null
  private val mutableFields: MutableList<OwnProperty<T, *>> = ArrayList()
  private val mutableSuperTypes: MutableList<ObjType<*>> = ArrayList()

  fun addField(field: OwnProperty<T, *>) {
    mutableFields.add(field)
  }

  fun addSuperType(type: ObjType<*>) {
    mutableSuperTypes.add(type)
  }

  override val typeParameters: List<TypeParameter>
    get() = emptyList()
  override val fields: List<OwnProperty<T, *>>
    get() = mutableFields
  override val superTypes: List<ObjType<*>>
    get() = mutableSuperTypes
  override val objType: ObjType<*>
    get() = ObjTypeType

  override fun toString(): String = "$name (${module.name})"


  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false
    other as ObjClassImpl<*>
    return module == other.module && name == other.name
  }

  override fun hashCode(): Int {
    return 31 * module.hashCode() + name.hashCode()
  }
}
