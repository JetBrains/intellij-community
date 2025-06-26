// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileEditor.impl

import com.intellij.openapi.fileEditor.FileEditorComposite
import com.intellij.openapi.extensions.ExtensionPointName
import org.jetbrains.annotations.ApiStatus

/**
 * Represents a policy that defines whether a skeleton view should be shown
 * for a given file editor composite.
 *
 * If there is no policy registered for a given file editor composite, editor skeleton **will not** be shown.
 * If at least one policy returns true from [shouldShowSkeleton], editor skeleton will be shown.
 *
 * @see EditorSkeleton
 */
@ApiStatus.Internal
interface EditorSkeletonPolicy {
  /**
   * Determines whether a skeleton view should be displayed for the given file editor composite.
   *
   * @param fileEditorComposite the file editor composite to evaluate
   * @return true if a skeleton view should be displayed for the provided file editor composite, false otherwise
   *
   * @see EditorSkeleton
   */
  fun shouldShowSkeleton(fileEditorComposite: FileEditorComposite): Boolean

  companion object {
    private val EP_NAME: ExtensionPointName<EditorSkeletonPolicy> = ExtensionPointName<EditorSkeletonPolicy>("com.intellij.editorSkeletonPolicy")

    internal fun shouldShowSkeleton(fileEditorComposite: FileEditorComposite): Boolean {
      return EP_NAME.extensionList.any { it.shouldShowSkeleton(fileEditorComposite) }
    }
  }
}