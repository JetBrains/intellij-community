/*
 * Copyright (C) 2019 The Android Open Source Project
 * Modified 2026 by JetBrains s.r.o.
 * Copyright (C) 2026 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.compose.ide.plugin.resources.vectorDrawable.preview

import com.intellij.compose.ide.plugin.resources.vectorDrawable.rendering.use
import com.intellij.compose.ide.plugin.shared.ComposeIdeBundle
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorState
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.withContext
import java.awt.BorderLayout
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import com.intellij.openapi.observable.util.addComponentListener
import java.awt.image.BufferedImage
import java.beans.PropertyChangeListener
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.SwingConstants
import kotlin.time.Duration.Companion.milliseconds

@OptIn(FlowPreview::class)
internal class ComposeResourcePreviewEditor(
  private val file: VirtualFile,
  private val document: Document?,
  coroutineScope: CoroutineScope,
) : UserDataHolderBase(), FileEditor {

  private val rootPanel = JBPanel<JBPanel<*>>(BorderLayout()).apply {
    isFocusable = true
  }

  private val imageCanvas = FitCenterImagePanel()

  private val updateRequests = MutableSharedFlow<Unit>(
    replay = 1,
    onBufferOverflow = BufferOverflow.DROP_OLDEST
  )

  init {
    rootPanel.add(imageCanvas, BorderLayout.CENTER)

    updateRequests
      .debounce(100.milliseconds)
      .onStart { emit(Unit) }
      .onEach { renderAndUpdatePreview() }
      .launchIn(coroutineScope)

    rootPanel.addComponentListener(this, object : ComponentAdapter() {
      override fun componentResized(e: ComponentEvent) {
        updateRequests.tryEmit(Unit)
      }
    })

    document?.addDocumentListener(object : DocumentListener {
      override fun documentChanged(event: DocumentEvent) {
        updateRequests.tryEmit(Unit)
      }
    }, this)
  }

  private suspend fun renderAndUpdatePreview() {
    val text = readAction { document?.text }
    if (text.isNullOrBlank()) {
      showMessage(ComposeIdeBundle.message("compose.resource.preview.empty.file.name"))
      return
    }

    val targetWidth = rootPanel.width
    val targetHeight = rootPanel.height

    if (targetWidth <= 0 || targetHeight <= 0) {
      clearUI()
      return
    }

    val result = try {
      withContext(Dispatchers.Default) {
        val renderer = BaseVectorDrawablePreviewRenderer.getInstance()
                       ?: return@withContext RenderResult.Error(ComposeIdeBundle.message("compose.resource.preview.render.error.name",
                                                                                         "No renderer available"))

        when (val res = renderer.renderPreview(text, targetWidth, targetHeight)) {
          is BaseVectorDrawablePreviewRenderer.RenderResult.Success -> RenderResult.Success(res.image)
          is BaseVectorDrawablePreviewRenderer.RenderResult.Error -> RenderResult.Error(ComposeIdeBundle.message("compose.resource.preview.render.error.name",
                                                                                                                 res.message))
        }
      }
    }
    catch (e: Exception) {
      if (Logger.shouldRethrow(e)) throw e
      RenderResult.Error(ComposeIdeBundle.message("compose.resource.preview.render.error.name", e.message ?: "Unknown Error"))
    }

    when (result) {
      is RenderResult.Success -> showImage(result.image)
      is RenderResult.Error -> showMessage(result.message)
    }
  }

  private suspend fun clearUI() = withContext(Dispatchers.EDT) {
    imageCanvas.image = null
  }

  private suspend fun showImage(image: BufferedImage) = withContext(Dispatchers.EDT) {
    imageCanvas.image = image

    if (rootPanel.components.firstOrNull() != imageCanvas) {
      rootPanel.removeAll()
      rootPanel.add(imageCanvas, BorderLayout.CENTER)
      rootPanel.revalidate()
      rootPanel.repaint()
    }
  }

  private suspend fun showMessage(message: @NlsContexts.DialogMessage String) = withContext(Dispatchers.EDT) {
    imageCanvas.image = null
    val label = JBLabel(message, SwingConstants.CENTER)

    rootPanel.removeAll()
    rootPanel.add(label, BorderLayout.CENTER)
    rootPanel.revalidate()
    rootPanel.repaint()
  }

  private sealed class RenderResult {
    data class Success(val image: BufferedImage) : RenderResult()
    data class Error(val message: @NlsContexts.DialogMessage String) : RenderResult()
  }

  override fun getComponent(): JComponent = rootPanel
  override fun getPreferredFocusedComponent(): JComponent = rootPanel
  override fun getName(): String = ComposeIdeBundle.message("compose.resource.preview.editor.name")
  override fun setState(state: FileEditorState) {}
  override fun isModified(): Boolean = false
  override fun isValid(): Boolean = file.isValid
  override fun addPropertyChangeListener(listener: PropertyChangeListener) {}
  override fun removePropertyChangeListener(listener: PropertyChangeListener) {}
  override fun getFile(): VirtualFile = file
  override fun dispose() {}
}

/** Lightweight canvas that smoothly stretches the image */
private class FitCenterImagePanel : JPanel() {
  var image: BufferedImage? = null
    set(value) {
      if (field === value) return
      field = value
      repaint()
    }

  init {
    isOpaque = false
  }

  override fun paintComponent(g: Graphics) {
    super.paintComponent(g)
    val img = image ?: return

    (g.create() as Graphics2D).use { g2d ->
      val availableW = width
      val availableH = height

      if (availableW <= 0 || availableH <= 0) return

      val scale = minOf(availableW.toDouble() / img.width, availableH.toDouble() / img.height)

      val drawW = (img.width * scale).toInt()
      val drawH = (img.height * scale).toInt()

      val dx = (width - drawW) / 2
      val dy = (height - drawH) / 2

      if (drawW != img.width || drawH != img.height) {
        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
      }
      g2d.drawImage(img, dx, dy, drawW, drawH, null)
    }
  }
}