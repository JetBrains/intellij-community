// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.zombie

import com.intellij.openapi.application.EDT
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext


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

  override suspend fun shouldSpawnZombie(recipe: SpawnRecipe): Boolean = true

  final override fun turnIntoZombie(recipe: TurningRecipe): Nothing? = null
  final override suspend fun shouldBuryZombie(recipe: TurningRecipe, zombie: Nothing): Boolean = false
  final override suspend fun buryZombie(id: Int, zombie: FingerprintedZombie<Nothing>?): Unit = Unit
  final override suspend fun exhumeZombie(id: Int): Nothing? = null
  final override suspend fun spawnZombie(recipe: SpawnRecipe, zombie: Nothing?): Unit = spawn(recipe)
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

abstract class CleaverNecromancer<Z : LimbedZombie<L>, L>(
  project: Project,
  coroutineScope: CoroutineScope,
  graveName: String,
  private val necromancy: LimbedNecromancy<Z, L>,
) : GravingNecromancer<Z>(project, coroutineScope, graveName, necromancy) {

  open fun isZombieFriendly(recipe: Recipe): Boolean {
    return true
  }

  open suspend fun spawnNoZombie(recipe: SpawnRecipe) {
  }

  abstract fun cutIntoLimbs(recipe: TurningRecipe): List<L>

  abstract suspend fun spawnZombie(
    recipe: SpawnRecipe,
    limbs: List<L>,
  ): (suspend (Editor) -> Unit)?

  final override fun turnIntoZombie(recipe: TurningRecipe): Z? {
    if (isZombieFriendly(recipe)) {
      val limbs = cutIntoLimbs(recipe)
      if (limbs.isNotEmpty()) {
        return necromancy.formZombie(limbs)
      }
    }
    return null
  }

  final override suspend fun spawnZombie(recipe: SpawnRecipe, zombie: Z?) {
    if (isZombieFriendly(recipe) && zombie != null) {
      val applyOnEdt = spawnZombie(recipe, zombie.limbs())
      if (applyOnEdt != null) {
        val editor = recipe.editorSupplier.invoke()
        withContext(Dispatchers.EDT) {
          if (recipe.isValid()) {
            applyOnEdt(editor)
          }
        }
      }
    } else {
      spawnNoZombie(recipe)
    }
  }
}
