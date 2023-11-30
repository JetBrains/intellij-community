// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.codegen.deft.meta

import com.intellij.workspaceModel.codegen.deft.annotations.Cached
import com.intellij.workspaceModel.codegen.deft.annotations.Parent

interface ObjType<T> : Obj {
  val classifier: ObjType<T>

  val arguments: List<ObjType<*>>
}

/**
 * Describes parameterized type of [Objects][Obj].
 *
 * Read first: "Values, References and Objects" in [Obj] docs.
 *
 * For implementation simplicity, [ObjClass] is used to define both [ObjClass]
 * itself and [asStructure], even when you need [asStructure] only.
 *
 * Objects can have [own][OwnProperty] and [extension][ExtProperty] fields.
 */
interface ObjClass<T : Obj> : ObjType<T> {
  val typeParameters: List<TypeParameter>

  val superTypes: List<ObjType<*>>

  @Parent
  val module: ObjModule

  val name: String

  val openness: Openness

  enum class Openness(
    val instantiatable: Boolean = false,
    val extendable: Boolean = false,
    val openHierarchy: Boolean = extendable
  ) {
    /** Single instance */
    `const`,

    /** Can't be extended */
    `final`(instantiatable = true),

    /** Should be extended (can't be instantiated directly) */
    `abstract`(extendable = true),

    /** Should be extended with sealed hierarchy */
    `enum`(extendable = true, openHierarchy = false),

    /** Can be extended and can be instantiated */
    `open`(instantiatable = true, extendable = true)
  }

  val fields: List<OwnProperty<T, *>>

  val parentField: OwnProperty<T, *>?

  val nameField: OwnProperty<T, *>?

  val fieldsByName: Map<String, OwnProperty<in T, *>>
    @Cached get() = fields.associateBy { it.name }

  val fieldsByLocalId: Map<Int, OwnProperty<in T, *>>
    @Cached get() = fields.associateBy { it.classLocalId }

  val asStructure: ValueType.Structure<T>
    @Cached get() = ValueType.Structure(fields.mapTo(mutableListOf()) { it.valueType })

  companion object
}

interface TypeParameter : Obj {
  val name: String
  val lowerBound: ObjType<*>
}
