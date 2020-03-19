// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.comment.ui

import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.openapi.project.DumbAware

abstract class GHPRAddCommentGutterIconRenderer : GutterIconRenderer(), DumbAware, Disposable {

  abstract val line: Int

  abstract fun disposeInlay()

  override fun dispose() {
    disposeInlay()
  }
}