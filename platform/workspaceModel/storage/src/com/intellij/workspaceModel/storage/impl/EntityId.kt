// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspaceModel.storage.impl

import com.intellij.workspaceModel.storage.WorkspaceEntity

internal fun createEntityId(arrayId: Int, clazz: Int): EntityId {
  return createPackedEntityId(arrayId, clazz)
}

/*
internal data class EntityId(val arrayId: Int, val clazz: Int) {
  init {
    if (arrayId < 0) error("ArrayId cannot be negative: $arrayId")
  }

  override fun toString(): String = clazz.findEntityClass<WorkspaceEntity>().simpleName + "-:-" + arrayId.toString()
}
*/

// Implementation of EntityId that is packed to a single long

private fun createPackedEntityId(arrayId: Int, clazz: Int) = arrayId.toLong() shl 32 or (clazz.toLong() and 0xffffffffL)

internal typealias EntityId = Long

internal const val invalidEntityId: EntityId = -1

val EntityId.arrayId: Int
  get() {
    assert(this >= 0)
    return (this shr 32).toInt()
  }

val EntityId.clazz: Int
  get() {
    assert(this >= 0)
    return this.toInt()
  }

fun EntityId.asString() = if (this >= 0) clazz.findEntityClass<WorkspaceEntity>().simpleName + "-:-" + arrayId.toString() else "UNINITIALIZED"

fun EntityId.copy(arrayId: Int = this.arrayId, clazz: Int = this.clazz): EntityId {
  return createEntityId(arrayId, clazz)
}

