// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileEditor.impl

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.fileEditor.FileEditorComposite
import com.intellij.platform.ide.productMode.IdeProductMode
import com.intellij.util.ThreeState
import org.jetbrains.annotations.ApiStatus

/**
 * Decides whether a file editor composite shows the editor skeleton while its editors load, and how long the skeleton fades in.
 *
 * The platform asks the policies in the order of the extension point. The first policy that does not answer [ThreeState.UNSURE] decides.
 * When no policy decides, the composite shows no skeleton. On the backend, a composite never shows a skeleton.
 *
 * @see com.intellij.openapi.fileEditor.impl.skeleton.EditorSkeletonNew
 */
@ApiStatus.Internal
interface EditorSkeletonPolicy {
  /**
   * Returns [ThreeState.YES] to show the skeleton for [fileEditorComposite], [ThreeState.NO] to show none,
   * or [ThreeState.UNSURE] to leave the decision to the next policy.
   * [ThreeState.YES] does not guarantee a skeleton, because the editors can be ready first.
   */
  fun shouldShowSkeleton(fileEditorComposite: FileEditorComposite): ThreeState

  /**
   * Returns the fade-in duration of the skeleton for [fileEditorComposite] in milliseconds,
   * or `null` to leave the decision to the next policy.
   */
  fun getSkeletonFadeInMs(fileEditorComposite: FileEditorComposite): Long? = null

  companion object {
    private val EP_NAME: ExtensionPointName<EditorSkeletonPolicy> = ExtensionPointName("com.intellij.editorSkeletonPolicy")

    internal fun shouldShowSkeleton(fileEditorComposite: FileEditorComposite): Boolean {
      if (IdeProductMode.isBackend) return false

      for (policy in EP_NAME.extensionList) {
        val shouldShow = policy.shouldShowSkeleton(fileEditorComposite)
        if (shouldShow == ThreeState.UNSURE) continue
        return shouldShow.toBoolean()
      }
      return false
    }

    internal fun fadeInMs(fileEditorComposite: FileEditorComposite): Long {
      for (policy in EP_NAME.extensionList) {
        val fadeInMs = policy.getSkeletonFadeInMs(fileEditorComposite) ?: continue
        return fadeInMs
      }
      return DEFAULT_FADE_IN_MS
    }

    /**
     * The fade-in duration when no policy decides.
     * It is also the default of the `editor.skeleton.delay.ms` registry key.
     */
    internal const val DEFAULT_FADE_IN_MS: Long = 1000
  }
}
