// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileChooser.universal

import com.intellij.icons.AllIcons
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.NlsSafe
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.listCellRenderer.listCellRenderer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.Point
import java.nio.file.Path
import java.util.function.BooleanSupplier
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener

internal class NioPathTextField(val scope: CoroutineScope, val chooseFiles: Boolean) : JBTextField() {

  var showHiddenSupplier: BooleanSupplier = BooleanSupplier { false }

  private var completionPopup: JBPopup? = null

  init {
    document.addDocumentListener(object : DocumentListener {
      override fun insertUpdate(e: DocumentEvent) {
        if (e.length == 1) {
          try {
            val inserted = e.document.getText(e.offset, e.length)
            if (inserted == "/" || inserted == "\\") {
              val slashOffset = e.offset
              val x = try {
                val anchorOffset = (slashOffset + 1).coerceIn(0, document.length)
                val rect = modelToView2D(anchorOffset)
                rect?.x?.toInt() ?: 0
              }
              catch (_: Exception) {
                0
              }
              showCompletion(x)
            }
          }
          catch (_: Exception) {
          }
        }
      }

      override fun removeUpdate(e: DocumentEvent) {}
      override fun changedUpdate(e: DocumentEvent) {}
    })
  }

  private fun showCompletion(x: Int) {
    closeCompletionPopup()
    val currentText = text
    val directory = try {
      Path.of(currentText)
    }
    catch (_: Exception) {
      return
    }

    val showHidden = showHiddenSupplier.asBoolean
    scope.launch {
      val children = withContext(Dispatchers.IO) {
        NioFileChooserUtil.safeGetChildren(directory, showHidden, showFiles = chooseFiles)
      }
      if (children.isNotEmpty()) {
        @Suppress("ForbiddenInSuspectContextMethod") // ModalityState.any() is required.
        ApplicationManager.getApplication().invokeLater({
          if (!isShowing || text != currentText) return@invokeLater
          showCompletionPopup(children, x)
        }, ModalityState.any())
      }
    }
  }

  private fun showCompletionPopup(children: List<Path>, x: Int) {
    closeCompletionPopup()
    val popup = JBPopupFactory.getInstance()
      .createPopupChooserBuilder(children)
      .setRenderer(listCellRenderer("") {
        icon(NioFileChooserUtil.getIcon(value) ?: AllIcons.Empty)
        @NlsSafe val name = value.fileName?.toString() ?: value.toString()
        text(name)
      })
      .setNamerForFiltering { path -> path.fileName?.toString() ?: path.toString() }
      .setItemChosenCallback { chosen ->
        val name = chosen.fileName?.toString() ?: return@setItemChosenCallback
        val caretPos = caretPosition
        document.insertString(caretPos, name, null)
      }
      .setRequestFocus(true)
      .createPopup()
    completionPopup = popup
    popup.show(RelativePoint(this, Point(x, height)))
  }

  private fun closeCompletionPopup() {
    completionPopup?.cancel()
    completionPopup = null
  }
}
