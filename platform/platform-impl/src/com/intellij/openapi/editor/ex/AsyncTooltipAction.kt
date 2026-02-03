// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.ex

import com.intellij.codeInsight.daemon.DaemonBundle
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.Service.Level
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ex.util.EditorUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsActions
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import com.intellij.platform.util.coroutines.childScope
import kotlinx.coroutines.*
import org.jetbrains.annotations.ApiStatus
import java.awt.event.InputEvent
import java.util.concurrent.atomic.AtomicReference


/**
 * Represents tooltipAction that is not ready immediately but requires fetching data for a noticeable amount of time, e.g., loading over network.
 *
 * Although methods `getText`, `execute`, and `showAllActions` work as if it were called with loaded tooltipAction,
 * it is not recommended to use them since they initiate a modal window.
 *
 * Currently, only platform `DaemonTooltipWithActionRenderer` is fully compatible with it
 */
@ApiStatus.Experimental
@ApiStatus.Internal
interface AsyncTooltipAction : TooltipAction {

  /**
   * Returns whether tooltipAction is loaded.
   *
   * Once `isLoaded` becomes `true`, it always remains `true`
   */
  fun isLoaded(): Boolean

  /**
   * Returns tooltipAction if it is loaded, otherwise throws `IllegalStateException`
   */
  fun getLoaded(): TooltipAction?

  /**
   * Invokes `block` immediately if tooltipAction is loaded, otherwise schedules `block` on EDT
   */
  fun invokeWhenLoaded(block: (TooltipAction?) -> Unit)

  companion object {

    /**
     * Schedules tooltipAction loading and returns a wrapper object
     */
    fun startLoading(editor: Editor, tooltipActionLoader: suspend () -> TooltipAction?): AsyncTooltipAction {
      val project = editor.project
      check(project != null)
      val editorCoroutineScope = project.service<MyService>().cs.childScope("editorCoroutineScope")
      EditorUtil.disposeWithEditor(editor) { editorCoroutineScope.cancel() }
      val loadingTooltipAction = editorCoroutineScope.async {
        tooltipActionLoader()
      }
      return AsyncTooltipActionImpl(project, editorCoroutineScope, loadingTooltipAction)
    }
  }
}


private class AsyncTooltipActionImpl(
  private val project: Project,
  private val coroutineScope: CoroutineScope,
  private val loadingTooltipAction: Deferred<TooltipAction?>,
) : AsyncTooltipAction {

  /**
   * 1) `LoadingTooltipAction` -> action loading
   * 2) `null` -> empty action loaded or error occurred
   * 3) `TooltipAction` -> action successfully loaded
   */
  private val loadedTooltipAction: AtomicReference<TooltipAction?> = AtomicReference(LoadingTooltipAction)

  init {
    coroutineScope.launch {
      runCatching {
        loadingTooltipAction.await()
      }.onSuccess { tooltipAction ->
        loadedTooltipAction.set(tooltipAction)
      }.onFailure { error ->
        loadedTooltipAction.set(null)
        throw error
      }
    }
  }

  override fun isLoaded(): Boolean {
    return loadedTooltipAction.get() != LoadingTooltipAction
  }

  override fun getLoaded(): TooltipAction? {
    val loaded = loadedTooltipAction.get()
    check(loaded != LoadingTooltipAction) { "tooltipAction is not loaded yet" }
    return loaded
  }

  override fun invokeWhenLoaded(block: (TooltipAction?) -> Unit) {
    val loaded = loadedTooltipAction.get()
    if (loaded != LoadingTooltipAction) {
      // fast path if already loaded
      block(loaded)
    } else {
      coroutineScope.launch {
        val loaded = loadingTooltipAction.await()
        withContext(Dispatchers.EDT) { // TODO: modality?
          block(loaded)
        }
      }
    }
  }

  /**
   * Fallback if someone is not ready to handle async loading.
   *
   * Currently, only platform `DaemonTooltipWithActionRenderer` is ready for it
   */
  override fun getText(): @NlsActions.ActionText String {
    return invokeWhenLoadedBlocking { tooltipAction ->
      tooltipAction!!.text
    }
  }

  /**
   * Fallback if someone is not ready to handle async loading.
   *
   * Currently, only platform `DaemonTooltipWithActionRenderer` is ready for it
   */
  override fun execute(editor: Editor, event: InputEvent?) {
    invokeWhenLoadedBlocking { tooltipAction ->
      tooltipAction!!.execute(editor, event)
    }
  }

  /**
   * Fallback if someone is not ready to handle async loading.
   *
   * Currently, only platform `DaemonTooltipWithActionRenderer` is ready for it
   */
  override fun showAllActions(editor: Editor) {
    invokeWhenLoadedBlocking { tooltipAction ->
      tooltipAction!!.showAllActions(editor)
    }
  }

  private fun <T> invokeWhenLoadedBlocking(block: (TooltipAction?) -> T): T {
    val loaded = loadedTooltipAction.get()
    return if (loaded != LoadingTooltipAction) {
      // fast path if already loaded
      block(loaded)
    } else {
      val title = DaemonBundle.message("daemon.tooltip.loading.actions.text")
      runWithModalProgressBlocking(project, title) {
        val loaded = loadingTooltipAction.await()
        block(loaded)
      }
    }
  }
}

/**
 * Utility object to distinguish between tooltip loading and loaded null tooltip
 */
private object LoadingTooltipAction : TooltipAction {

  override fun getText(): @NlsActions.ActionText String {
    throw IllegalStateException("tooltipAction is loading")
  }

  override fun execute(editor: Editor, event: InputEvent?) {
    throw IllegalStateException("tooltipAction is loading")
  }

  override fun showAllActions(editor: Editor) {
    throw IllegalStateException("tooltipAction is loading")
  }
}

@Service(Level.PROJECT)
private class MyService(val cs: CoroutineScope)
