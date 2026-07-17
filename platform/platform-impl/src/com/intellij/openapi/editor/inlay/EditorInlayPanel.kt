// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.inlay

import java.awt.BorderLayout
import java.awt.event.ActionEvent
import java.awt.event.KeyEvent
import javax.swing.AbstractAction
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.KeyStroke

internal class EditorInlayPanel(
  style: EditorInlayStyle,
  header: EditorInlayHeader?,
  content: JComponent,
) : JPanel(BorderLayout()) {
  var closeHandler: (() -> Unit)? = null

  init {
    isOpaque = false
    style.applyTo(this)

    val card = style.createCard()
    if (header != null) {
      card.add(header.createComponent { closeHandler?.invoke() }, BorderLayout.NORTH)
    }
    card.add(content, BorderLayout.CENTER)
    add(card, BorderLayout.CENTER)

    val closeActionKey = "closeEditorInlay"
    getInputMap(WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), closeActionKey)
    actionMap.put(closeActionKey, object : AbstractAction() {
      override fun actionPerformed(event: ActionEvent) {
        closeHandler?.invoke()
      }
    })
  }
}
