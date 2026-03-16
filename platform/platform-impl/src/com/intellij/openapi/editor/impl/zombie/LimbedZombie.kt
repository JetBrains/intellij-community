// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.zombie


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
) : AbstractNecromancy<Z>(spellLevel) {

  abstract fun formZombie(limbs: List<L>): Z

  protected abstract fun Out.writeLimb(limb: L)

  protected abstract fun In.readLimb(): L

  final override fun Out.writeZombie(zombie: Z) {
    writeList(zombie.limbs()) {
      writeLimb(it)
    }
  }

  final override fun In.readZombie(): Z {
    val limbs = readList {
      readLimb()
    }
    return formZombie(limbs)
  }
}
