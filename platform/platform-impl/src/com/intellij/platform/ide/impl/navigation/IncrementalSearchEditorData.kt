// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.impl.navigation

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.NlsContexts
import com.intellij.ui.LightweightHint
import org.jetbrains.annotations.ApiStatus

/**
 * Holds the state of the incremental search in an editor.
 */
@ApiStatus.Internal
class IncrementalSearchEditorData {
  @JvmField
  var hint: LightweightHint? = null

  @JvmField
  var lastSearch: @NlsContexts.Label String? = null

  companion object {
    @JvmField
    val KEY: Key<IncrementalSearchEditorData> = Key.create("IncrementalSearchHandler.SEARCH_DATA_IN_EDITOR_VIEW_KEY")

    /**
     * Returns `true` if the incremental search hint shows in [editor].
     */
    @JvmStatic
    fun isHintVisible(editor: Editor): Boolean {
      val hint = editor.getUserData(KEY)?.hint
      return hint != null && hint.isVisible
    }
  }
}
