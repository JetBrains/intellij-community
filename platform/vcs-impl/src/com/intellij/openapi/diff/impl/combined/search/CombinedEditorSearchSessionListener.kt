// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.diff.impl.combined.search

import com.intellij.openapi.editor.Editor
import java.util.EventListener

/**
 * A listener for events related to [CombinedEditorSearchSession] (session with multiple editor session aggregated)
 */
interface CombinedEditorSearchSessionListener : EventListener {

  /**
   * Callback when during next/previous invocation the current [editor] changed.
   * Implementors may want to scroll to such [editor].
   *
   * @param forward search direction
   * @param editor the editor where the search is performed
   */
  fun onSearch(forward: Boolean, editor: Editor)

  /**
   * Notify that the status text is changed. Found [matches] within [files].
   *
   * @param matches the number of matches found
   * @param files the number of files processed
   */
  fun statusTextChanged(matches: Int, files: Int)
}
