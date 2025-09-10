// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.zombie

import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.FoldRegion
import com.intellij.openapi.editor.ex.util.EditorUtil
import com.intellij.openapi.editor.impl.FoldingKeys
import com.intellij.openapi.util.Key
import com.intellij.platform.util.coroutines.childScope
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageEditorUtil
import kotlinx.coroutines.*
import org.jetbrains.annotations.ApiStatus
import kotlin.time.Duration.Companion.seconds

object CodeFoldingZombieUtils {
  private val ZOMBIE_RAISED_KEY: Key<Boolean> = Key.create("zombie.raised.in.editor")
  private val ZOMBIE_CLEANUP_CONTEXT_KEY: Key<CoroutineScope> = Key.create("zombie.cleanup.context.in.editor")
  
  @ApiStatus.Internal
  fun getZombieRegions(editor: Editor, removeFiltered: Boolean, zombieFilter: (FoldRegion) -> Boolean): List<FoldRegion> {
    val foldingModel = editor.foldingModel
    val filteredZombie = foldingModel
      .allFoldRegions
      .filter { region -> region.getUserData(FoldingKeys.ZOMBIE_REGION_KEY) != null && zombieFilter(region) }
      .toList()
    if (removeFiltered) {
      for (uselessZombie in filteredZombie) {
        foldingModel.removeFoldRegion(uselessZombie)
      }
    }
    return filteredZombie 
  }

  @ApiStatus.Internal
  fun setZombieRaised(editor: Editor, raised: Boolean): Unit =  ZOMBIE_RAISED_KEY.set(editor, raised)

  @ApiStatus.Internal
  fun isZombieRaised(editor: Editor): Boolean = ZOMBIE_RAISED_KEY.get(editor) ?: false

  private val waitingForNovaAndRiderBackendInitializationCompleteIfAny = 5.seconds

  @ApiStatus.Internal
  fun postponeAndScheduleCleanupZombieRegions(editor: Editor) {
    // There may be a huge amount injected editors, but only the top editor is zombies' owner.
    // Covered by the test: InjectedLanguageEditingTest.testTypingNearBigHeapOfInjectedFragmentsDoesNotCauseTooManyRangeMarkersAllocated 
    if (!isZombieRaised(editor) || InjectedLanguageEditorUtil.getTopLevelEditor(editor) != editor) {
      return
    }

    createZombieCleanerScope(editor)?.launch {
      while (!editor.isDisposed) {
        if (!isZombieRaised(editor)) break
        delay(waitingForNovaAndRiderBackendInitializationCompleteIfAny)
        if (isZombieRaised(editor)) {
          withContext(Dispatchers.EDT) {
            editor.foldingModel.runBatchFoldingOperationDoNotCollapseCaret {
              getZombieRegions(editor, true) { true }
            }
          }
          setZombieRaised(editor, false)
          break
        }
      }
    }
  }

  private fun createZombieCleanerScope(editor: Editor): CoroutineScope? {
    ZOMBIE_CLEANUP_CONTEXT_KEY.get(editor)?.cancel()
    
    val project = editor.project
    if (project == null || project.isDisposed) return null
    val editorCoroutineScope = project.service<ZombieCleanupService>().scope.childScope("editorZombieCleaner")
    EditorUtil.disposeWithEditor(editor) { editorCoroutineScope.cancel() }

    ZOMBIE_CLEANUP_CONTEXT_KEY.set(editor, editorCoroutineScope)
    return editorCoroutineScope
  }
  
  @Service(Service.Level.PROJECT)
  private class ZombieCleanupService(val scope: CoroutineScope) 
}