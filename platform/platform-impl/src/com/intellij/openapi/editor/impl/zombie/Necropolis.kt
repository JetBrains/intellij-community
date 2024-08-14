// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.zombie

import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.application.WriteIntentReadAction
import com.intellij.openapi.application.ex.ApplicationManagerEx
import com.intellij.openapi.application.readActionBlocking
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.ControlFlowException
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.EditorKind
import com.intellij.openapi.editor.event.EditorFactoryEvent
import com.intellij.openapi.editor.event.EditorFactoryListener
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileWithId
import kotlinx.coroutines.*
import org.jetbrains.annotations.TestOnly
import java.nio.file.Path
import java.util.concurrent.CancellationException
import java.util.concurrent.atomic.AtomicReference

private val LOG: Logger = logger<Necropolis>()

private val NECROMANCER_EP = ExtensionPointName<NecromancerAwaker<Zombie>>("com.intellij.textEditorNecromancerAwaker")

internal fun necropolisPath(): Path {
  return PathManager.getSystemDir().resolve("editor")
}

/**
 * Service managing all necromancers.
 *
 * See [Zombie]
 */
@Service(Service.Level.PROJECT)
class Necropolis(private val project: Project, private val coroutineScope: CoroutineScope) : Disposable {

  private val necromancersDeferred: Deferred<List<Necromancer<Zombie>>>
  private val necromancersRef: AtomicReference<List<Necromancer<Zombie>>> = AtomicReference()

  init {
    necromancersDeferred = coroutineScope.async {
      service<NecropolisDestroyer>().cleanGravesIfNeeded()
      val necromancers = createAwakers().map {
        async(Dispatchers.IO + CoroutineName(it.javaClass.name)) {
          it.awake(project, coroutineScope)
        }
      }.awaitAll()
      necromancersRef.set(necromancers)
      subscribeEditorClosed(necromancers)
      necromancers
    }
  }

  suspend fun spawnZombies(
    project: Project,
    file: VirtualFile,
    document: Document,
    editorSupplier: suspend () -> EditorEx,
    highlighterReady: suspend () -> Unit,
  ) {
    require(project == this.project)
    if (!project.isDisposed && !project.isDefault && file is VirtualFileWithId) {
      val fileId = file.id
      val (modStamp, documentContent) = readActionBlocking {
        // get consistent modStamp with docContent under RA
        document.modificationStamp to document.immutableCharSequence
      }
      val fingerprint = FingerprintedZombieImpl.captureFingerprint(documentContent)
      val recipe = SpawnRecipe(project, fileId, file, document, modStamp, editorSupplier, highlighterReady)
      coroutineScope {
        for (necromancer in necromancersDeferred.await()) {
          launch(CoroutineName(necromancer.name())) {
            try {
              if (!project.isDisposed && necromancer.shouldSpawnZombie(recipe)) {
                val zombie = exhumeZombieIfValid(recipe, necromancer, fingerprint)
                necromancer.spawnZombie(recipe, zombie)
              }
            } catch (e: CancellationException) {
              throw e
            } catch (e: Throwable) {
              LOG.warn(
                "Exception during editor loading",
                if (e is ControlFlowException) RuntimeException(e) else e
              )
            }
          }
        }
      }
    }
  }

  private fun subscribeEditorClosed(necromancers: List<Necromancer<Zombie>>) {
    EditorFactory.getInstance().addEditorFactoryListener(
      object : EditorFactoryListener {
        override fun editorReleased(event: EditorFactoryEvent) {
          val recipe = createTurningRecipe(event)
          if (recipe != null) {
            //maybe readaction
            WriteIntentReadAction.run {
              turnIntoZombiesAndBury(necromancers, recipe)
            }
          }
        }
      },
      this,
    )
  }

  private fun createTurningRecipe(event: EditorFactoryEvent): TurningRecipe? {
    val editor = event.editor
    if (editor.editorKind == EditorKind.MAIN_EDITOR && editor.project == project) {
      val document = editor.document
      val file = FileDocumentManager.getInstance().getFile(document)
      if (file is VirtualFileWithId) {
        return TurningRecipe(project, file.id, file, document, document.modificationStamp, editor)
      }
    }
    return null
  }

  private fun turnIntoZombiesAndBury(necromancers: List<Necromancer<Zombie>>, recipe: TurningRecipe) {
    val zombies = necromancers.mapNotNull { necromancer ->
      necromancer.turnIntoZombie(recipe)?.let { zombie ->
        necromancer to zombie
      }
    }.toList()
    if (zombies.isNotEmpty()) {
      coroutineScope.launch {
        val documentContent = readActionBlocking {
          if (recipe.isValid()) {
            recipe.document.immutableCharSequence
          } else {
            null
          }
        }
        if (documentContent != null) {
          val fingerprint = FingerprintedZombieImpl.captureFingerprint(documentContent)
          for ((necromancer, zombie) in zombies) {
            val context = if (ApplicationManagerEx.isInIntegrationTest()){
              CoroutineName(necromancer.name()) + NonCancellable
            } else {
              CoroutineName(necromancer.name())
            }
            launch(context) {
              if (recipe.isValid() && necromancer.shouldBuryZombie(recipe, zombie)) {
                necromancer.buryZombie(recipe.fileId, FingerprintedZombieImpl(fingerprint, zombie))
              }
            }
          }
        }
      }
    }
  }

  private suspend fun exhumeZombieIfValid(
    recipe: SpawnRecipe,
    necromancer: Necromancer<Zombie>,
    fingerprint: Long,
  ): Zombie? {
    if (recipe.isValid()) {
      val fingerprinted = necromancer.exhumeZombie(recipe.fileId)
      if (fingerprinted?.fingerprint() == fingerprint) {
        return fingerprinted.zombie()
      }
    }
    return null
  }

  private fun createAwakers(): List<NecromancerAwaker<Zombie>> {
    val allAwakers = NECROMANCER_EP.filterableLazySequence()
      .filter {
        val isCore = it.pluginDescriptor.pluginId == PluginManagerCore.CORE_ID ||
                     it.implementationClassName == "com.jetbrains.rider.daemon.grave.RiderHighlightingNecromancerAwaker"
        if (!isCore) {
          LOG.error("Only core plugin can define ${NECROMANCER_EP.name}: ${it.pluginDescriptor}")
        }
        isCore
      }
      .mapNotNull { it.instance }
      .toList()
    return allAwakers
      .filter { !hasSubclass(it, allAwakers) } // workaround for Rider's overriding of HighlightingNecromancer
      .toList()
  }

  private fun hasSubclass(obj: Any, allObj: List<Any>): Boolean {
    for (o in allObj) {
      if (obj !== o && obj.javaClass.isAssignableFrom(o.javaClass)) {
        return true
      }
    }
    return false
  }

  override fun dispose() {
  }

  private fun getNecromancers(): List<Necromancer<Zombie>> {
    return necromancersRef.get() ?: throw IllegalStateException("necromancers are not initialized yet")
  }

  @TestOnly
  fun necromancerByName(name: String): Necromancer<Zombie> {
    return getNecromancers().find { it.name() == name }!!
  }

  override fun toString(): String {
    val necromancersStr = getNecromancers().joinToString(", ") { it.name() }
    return "Necropolis(project=${project.name}, necromancers=[$necromancersStr])"
  }
}
