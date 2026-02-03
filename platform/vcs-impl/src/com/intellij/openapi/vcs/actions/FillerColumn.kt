// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.actions

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.TextAnnotationGutterProvider
import com.intellij.openapi.util.Couple
import com.intellij.openapi.vcs.annotate.FileAnnotation
import com.intellij.openapi.vcs.annotate.TextAnnotationPresentation
import com.intellij.openapi.vcs.history.VcsRevisionNumber
import com.intellij.ui.ExperimentalUI
import org.jetbrains.annotations.ApiStatus
import java.awt.Color

/**
 * @author Konstantin Bulenkov
 */
@ApiStatus.Internal
class FillerColumn(annotation: FileAnnotation,
                   presentation: TextAnnotationPresentation,
                   colorScheme: Couple<out MutableMap<VcsRevisionNumber, Color>>?) : AnnotationFieldGutter(annotation, presentation,
                                                                                                           colorScheme), TextAnnotationGutterProvider.Filler {
  override fun getLineText(line: Int, editor: Editor?): String = ""

  override fun getWidth() = when {
    ExperimentalUI.isNewUI() -> 8
    else -> 0
  }

  override fun useMargin() = false
}