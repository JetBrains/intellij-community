// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.zombie

import java.io.DataInput
import java.io.DataOutput


/**
 * Base class for zombie consisting of limbs.
 *
 * See [LimbedNecromancy]
 */
open class LimbedZombie<L>(private val limbs: List<L>) : Zombie {
  fun limbs(): List<L> = limbs

  override fun toString(): String {
    return "${javaClass.simpleName}[limbs=${limbs.size}]"
  }
}

/**
 * Base necromancy serializing the zombie limb-by-limb
 */
abstract class LimbedNecromancy<Z : LimbedZombie<L>, L> (
  spellLevel: Int,
  isDeepBury: Boolean = false,
) : AbstractNecromancy<Z>(spellLevel, isDeepBury) {

  abstract fun formZombie(limbs: List<L>): Z

  protected abstract fun buryLimb(grave: DataOutput, limb: L)

  protected abstract fun exhumeLimb(grave: DataInput): L

  final override fun buryZombie(grave: DataOutput, zombie: Z) {
    writeInt(grave, zombie.limbs().size)
    for (limb in zombie.limbs()) {
      buryLimb(grave, limb)
    }
  }

  final override fun exhumeZombie(grave: DataInput): Z {
    val limbCount = readInt(grave)
    val limbs = buildList {
      repeat(limbCount) {
        add(exhumeLimb(grave))
      }
    }
    return formZombie(limbs)
  }
}
