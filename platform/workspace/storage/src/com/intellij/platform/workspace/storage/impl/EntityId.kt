// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.platform.workspace.storage.impl

internal fun createEntityId(arrayId: Int, clazz: Int): EntityId {
  return createPackedEntityId(arrayId, clazz)
}

/*
internal data class EntityId(val arrayId: Int, val clazz: Int) {
  init {
    if (arrayId < 0) error("ArrayId cannot be negative: $arrayId")
  }

  override fun toString(): String = clazz.findWorkspaceEntity().simpleName + "-:-" + arrayId.toString()
}
*/

// Implementation of EntityId that is packed to a single long

private fun createPackedEntityId(arrayId: Int, clazz: Int) = arrayId.toLong() shl 32 or (clazz.toLong() and 0xffffffffL)

internal typealias EntityId = Long

internal const val invalidEntityId: EntityId = -1

internal val EntityId.arrayId: Int
  get() {
    return (this shr 32).toInt().also {unpackedArrayId ->
      assert(unpackedArrayId >= 0) {
        "arrayId is $unpackedArrayId, but it should be >=0. EntityId is $this (0x${this.toULong().toString(16)})"
      }
    }
  }

internal val EntityId.clazz: Int
  get() {
    return this.toInt().also { unpackedClazz ->
      assert(unpackedClazz >= 0) {
        "clazz is $unpackedClazz, but it should be >=0. EntityId is $this (0x${this.toULong().toString(16)})"
      }
    }
  }

internal fun EntityId.asString() = if (this >= 0) clazz.findWorkspaceEntity().simpleName + "-:-" + arrayId.toString() else "UNINITIALIZED"

internal fun EntityId.copy(arrayId: Int = this.arrayId, clazz: Int = this.clazz): EntityId {
  return createEntityId(arrayId, clazz)
}

