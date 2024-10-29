// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.zombie

import com.intellij.openapi.project.Project
import com.intellij.util.io.DataInputOutputUtil.readINT
import com.intellij.util.io.DataInputOutputUtil.writeINT
import kotlinx.coroutines.CoroutineScope
import java.io.DataInput
import java.io.DataOutput

/**
 * Abstract class representing [Necromancer] who can manage zombies.
 *
 * See [Zombie]
 */
abstract class AbstractNecromancer<Z : Zombie>(private val name: String) : Necromancer<Z> {
  final override fun name(): String = name

  override fun toString(): String = "Necromancer[name=$name}]"
}

/**
 * The one without an ability to bury and exhume zombies
 */
abstract class WeakNecromancer(name: String) : AbstractNecromancer<Nothing>(name) {
  abstract suspend fun spawn(recipe: SpawnRecipe)
  override suspend fun shouldSpawnZombie(recipe: SpawnRecipe) = true

  final override fun turnIntoZombie(recipe: TurningRecipe) = null
  final override suspend fun shouldBuryZombie(recipe: TurningRecipe, zombie: Nothing) = false
  final override suspend fun buryZombie(id: Int, zombie: FingerprintedZombie<Nothing>?) = Unit
  final override suspend fun exhumeZombie(id: Int) = null
  final override suspend fun spawnZombie(recipe: SpawnRecipe, zombie: Nothing?) = spawn(recipe)
}

/**
 * The one who is powerful enough to bury and exhume zombies
 */
abstract class GravingNecromancer<Z : Zombie>(
  project: Project,
  coroutineScope: CoroutineScope,
  graveName: String,
  necromancy: Necromancy<Z>,
) : AbstractNecromancer<Z>(graveName) {
  private val grave: Grave<Z> = GraveImpl(graveName, necromancy, project, coroutineScope)

  override suspend fun shouldBuryZombie(recipe: TurningRecipe, zombie: Z): Boolean {
    return true
  }

  override suspend fun shouldSpawnZombie(recipe: SpawnRecipe): Boolean {
    return true
  }

  final override suspend fun buryZombie(id: Int, zombie: FingerprintedZombie<Z>?) {
    grave.buryZombie(id, zombie)
  }

  final override suspend fun exhumeZombie(id: Int): FingerprintedZombie<Z>? {
    return grave.exhumeZombie(id)
  }
}

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
  private val spellLevel: Int,
  private val isDeepBury: Boolean = false,
) : Necromancy<Z> {

  abstract fun buryLimb(grave: DataOutput, limb: L)

  abstract fun exhumeLimb(grave: DataInput): L

  abstract fun formZombie(limbs: List<L>): Z

  final override fun spellLevel(): Int = spellLevel

  final override fun isDeepBury(): Boolean = isDeepBury

  final override fun buryZombie(grave: DataOutput, zombie: Z) {
    writeINT(grave, zombie.limbs().size)
    for (limb in zombie.limbs()) {
      buryLimb(grave, limb)
    }
  }

  final override fun exhumeZombie(grave: DataInput): Z {
    val limbCount = readINT(grave)
    val limbs = buildList {
      repeat(limbCount) {
        add(exhumeLimb(grave))
      }
    }
    return formZombie(limbs)
  }
}
