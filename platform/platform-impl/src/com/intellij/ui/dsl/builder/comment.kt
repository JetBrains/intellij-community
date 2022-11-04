// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.dsl.builder

import com.intellij.openapi.observable.properties.ObservableProperty
import com.intellij.openapi.observable.util.bind
import javax.swing.JComponent
import javax.swing.JEditorPane


fun <T : JComponent> Cell<T>.applyToComment(action: JEditorPane.() -> Unit): Cell<T> = apply {
  val comment = requireNotNull(comment) { "Please specify `Cell.comment` before usage." }
  comment.action()
}

fun <T : JComponent> Cell<T>.bindCommentText(property: ObservableProperty<String>): Cell<T> =
  applyToComment { bind(property) }