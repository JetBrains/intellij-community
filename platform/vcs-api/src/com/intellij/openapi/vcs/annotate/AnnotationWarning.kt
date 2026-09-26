// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.annotate

import com.intellij.openapi.util.NlsContexts.Label
import com.intellij.openapi.util.NlsContexts.LinkLabel
import com.intellij.ui.EditorNotificationPanel
import com.intellij.ui.LightColors
import java.awt.Color

class AnnotationWarning private constructor(
  val message: @Label String,
  val actions: List<Action>,
  val backgroundColor: Color,
  val status: EditorNotificationPanel.Status,
  val showAnnotation: Boolean,
) {
  companion object {
    @JvmStatic
    @JvmOverloads
    fun warning(message: @Label String, actions: List<Action> = emptyList()): AnnotationWarning =
      AnnotationWarning(message, actions, LightColors.YELLOW, EditorNotificationPanel.Status.Warning, showAnnotation = true)

    @JvmStatic
    @JvmOverloads
    fun error(message: @Label String, actions: List<Action> = emptyList()): AnnotationWarning =
      AnnotationWarning(message, actions, LightColors.RED, EditorNotificationPanel.Status.Error, showAnnotation = false)
  }

  abstract class Action(val text: @LinkLabel String) {
    abstract fun doAction(hideWarning: Runnable)
  }
}