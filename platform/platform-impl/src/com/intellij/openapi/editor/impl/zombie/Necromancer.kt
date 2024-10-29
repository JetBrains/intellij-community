// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.zombie

import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import kotlinx.coroutines.CoroutineScope
import java.io.DataInput
import java.io.DataOutput

/**
 * Captured state of disposed text editor.
 * The main purpose is to improve the experience of opening the editor.
 * It is important to understand that zombie does not necessarily reduce the opening time of the editor
 * but creates an illusion of that by painting markup from the previous editor session.
 * Zombie should be serializable via [Necromancy]. Lifeecycle of zombie:
 *
 * closedEditor -> turnIntoZombie -> buryZombie -> exhumeZombie -> spawnZombie -> openedEditor
 *
 * The entry point for implementing the zombie mechanism is [NecromancerAwaker]
 */
interface Zombie

/**
 * The one who spawns zombies.
 *
 * No need to implement this interface, consider inheriting [GravingNecromancer].
 * If no state needs to be saved and restored, but you need to initialize the editor on BGT,
 * consider inheriting [WeakNecromancer]
 */
interface Necromancer<Z : Zombie> {

  /**
   * Unique name across all necromancers
   */
  fun name(): String

  /**
   * Turning editor's state into a zombie.
   * Called on the EDT (so far it is not safe to call it on BGT)
   */
  fun turnIntoZombie(recipe: TurningRecipe): Z?

  /**
   * Determines whether the zombie should be buried into grave.
   * Called on BGT
   */
  suspend fun shouldBuryZombie(recipe: TurningRecipe, zombie: Z): Boolean

  /**
   * Burying the zombie into its grave via an IO operation.
   * Called on BGT
   */
  suspend fun buryZombie(id: Int, zombie: FingerprintedZombie<Z>?)

  /**
   * Exhume the zombie from the grave via an IO operation.
   * Called on BGT
   */
  suspend fun exhumeZombie(id: Int): FingerprintedZombie<Z>?

  /**
   * Determines whether the zombie should be applied to the editor.
   * Called on BGT
   */
  suspend fun shouldSpawnZombie(recipe: SpawnRecipe): Boolean

  /**
   * Applies the zombie to an editor.
   * Called on BGT.
   * The implementation should check whether [recipe] is still valid after transition to EDT or acquired RA
   */
  suspend fun spawnZombie(recipe: SpawnRecipe, zombie: Z?)
}

/**
 * The one who awakes necromancer.
 *
 * It is an extension point to define a necromancer
 */
interface NecromancerAwaker<Z : Zombie> {
  fun awake(project: Project, coroutineScope: CoroutineScope): Necromancer<Z>
}

/**
 * Fingerprint is generated from document content and helps to determine
 * whether the zombie is still actual for the particular editor
 */
interface FingerprintedZombie<Z : Zombie> {
  fun fingerprint(): Long
  fun zombie(): Z
}

/**
 * The spell placing zombies underground
 */
interface Necromancy<Z : Zombie> {

  /**
   * Serde version which should be incremented on each format change
   */
  fun spellLevel(): Int

  /**
   * Serializes and writes zombie to output
   */
  fun buryZombie(grave: DataOutput, zombie: Z)

  /**
   * Reads and deserializes zombie from input
   */
  fun exhumeZombie(grave: DataInput): Z

  /**
   * Optimization hint whether zombie can stay in heap instead of spilling to disk.
   *
   * `true` if the zombie should be spilled to disk almost immediately,
   * otherwise it may remain in heap for some amount of time
   */
  fun isDeepBury(): Boolean
}

/**
 * The place where zombies are buried
 */
interface Grave<Z : Zombie> {
  suspend fun buryZombie(id: Int, zombie: FingerprintedZombie<Z>?)
  suspend fun exhumeZombie(id: Int): FingerprintedZombie<Z>?
}

data class TurningRecipe(
  val project: Project,
  val fileId: Int,
  val file: VirtualFile,
  val document: Document,
  val documentModStamp: Long,
  val editor: Editor,
) {
  fun isValid(): Boolean {
    return documentModStamp == document.modificationStamp
  }
}

data class SpawnRecipe(
  val project: Project,
  val fileId: Int,
  val file: VirtualFile,
  val document: Document,
  val documentModStamp: Long,
  val editorSupplier: suspend () -> EditorEx,
  val highlighterReady: suspend () -> Unit,
) {
  fun isValid(editor: Editor): Boolean {
    return isValid() && !editor.isDisposed
  }

  fun isValid(): Boolean {
    return !project.isDisposed && documentModStamp == document.modificationStamp
  }
}
