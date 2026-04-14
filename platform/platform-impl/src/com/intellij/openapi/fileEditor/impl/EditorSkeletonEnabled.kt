// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileEditor.impl

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.fileEditor.FileEditorComposite
import com.intellij.util.ThreeState
import org.jetbrains.annotations.ApiStatus

/**
 * Represents a policy that defines whether a skeleton view should be shown
 * for a given file editor composite.
 *
 * If there is no policy registered for a given file editor composite, editor skeleton **will not** be shown.
 * If all policies return true from [shouldShowSkeleton], editor skeleton will be shown.
 *
 * @see EditorSkeleton
 */
@ApiStatus.Internal
interface EditorSkeletonPolicy {
  /**
   * Determines whether a skeleton view should be displayed for the given file editor composite.
   *
   * @param fileEditorComposite the file editor composite to evaluate
   * @return `true` if a skeleton view should be displayed for the provided file editor composite, `false` otherwise.
   * Returning `true` does not guarantee that a skeleton view will be shown, returning `false` guarantees that a skeleton view will not be shown.
   *
   * @see EditorSkeleton
   */
  fun shouldShowSkeleton(fileEditorComposite: FileEditorComposite): ThreeState

  fun getSkeletonDelayMs(fileEditorComposite: FileEditorComposite): Long? = null

  companion object {
    private val EP_NAME: ExtensionPointName<EditorSkeletonPolicy> = ExtensionPointName("com.intellij.editorSkeletonPolicy")

    internal fun shouldShowSkeleton(fileEditorComposite: FileEditorComposite): Boolean {
      for (policy in EP_NAME.extensionList) {
        val shouldShow = policy.shouldShowSkeleton(fileEditorComposite)
        if (shouldShow == ThreeState.UNSURE) continue
        return shouldShow.toBoolean()
      }
      return false
    }

    internal fun getSkeletonDelayMs(fileEditorComposite: FileEditorComposite): Long {
      for (policy in EP_NAME.extensionList) {
        val delay = policy.getSkeletonDelayMs(fileEditorComposite) ?: continue
        return delay
      }
      return 300
    }
  }
}
