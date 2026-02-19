// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.zombie

import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.writeIntentReadAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext


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
  ): ((Editor) -> Unit)?

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
            writeIntentReadAction {
              if (recipe.isValid()) {
                applyOnEdt(editor)
              }
            }
          }
        }
      }
    } else {
      spawnNoZombie(recipe)
    }
  }
}
