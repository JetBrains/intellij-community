// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl

import com.intellij.diagnostic.rethrowControlFlowException
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.EditorKind
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.platform.diagnostic.telemetry.impl.span
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.ui.ImageUtil
import kotlinx.coroutines.Dispatchers
import org.jetbrains.annotations.ApiStatus
import java.awt.image.BufferedImage

private val LOG = logger<EditorWarmUp>()

private object EditorWarmUp

/**
 * Creates, lays out, paints off screen and releases one editor while the application starts, so the first editor of
 * the project does not load the editor classes, run their static initializers and link their lambda sites on the EDT.
 */
@ApiStatus.Internal
suspend fun warmUpEditor() {
  if (ApplicationManager.getApplication().isHeadlessEnvironment) {
    return
  }
  serviceAsync<EditorColorsManager>()
  val factory = serviceAsync<EditorFactory>()
  span("editor warm-up", Dispatchers.EDT) {
    try {
      val editor = factory.createEditor(factory.createDocument(SAMPLE_TEXT), null, EditorKind.MAIN_EDITOR)
      try {
        val component = editor.component
        component.setSize(JBUIScale.scale(800), JBUIScale.scale(600))
        component.validate()
        val image = ImageUtil.createImage(component.width, component.height, BufferedImage.TYPE_INT_ARGB)
        val g = image.createGraphics()
        try {
          component.paint(g)
        }
        finally {
          g.dispose()
        }
      }
      finally {
        factory.releaseEditor(editor)
      }
    }
    catch (e: Throwable) {
      rethrowControlFlowException(e)
      LOG.warn("The editor warm-up failed", e)
    }
  }
}

private val SAMPLE_TEXT = (1..40).joinToString("\n") { "line $it: the quick brown fox jumps over the lazy dog" }
