/*
 * Copyright (C) 2019 The Android Open Source Project
 * Modified 2026 by JetBrains s.r.o.
 * Copyright (C) 2026 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.compose.ide.plugin.resources.previewDrawables

import com.android.ide.common.vectordrawable.VdPreview
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorState
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.util.ui.JBUI
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.withContext
import java.awt.BorderLayout
import java.awt.image.BufferedImage
import java.beans.PropertyChangeListener
import javax.swing.ImageIcon
import javax.swing.JComponent
import javax.swing.SwingConstants
import kotlin.math.max
import kotlin.time.Duration.Companion.milliseconds

@OptIn(FlowPreview::class)
internal class ComposeResourcePreviewEditor(
  private val file: VirtualFile,
  private val document: Document?,
  coroutineScope: CoroutineScope,
) : UserDataHolderBase(), FileEditor, Disposable {

  private val panel = JBPanel<JBPanel<*>>(BorderLayout())

  private val updateRequests = MutableSharedFlow<Unit>(
    replay = 1,
    onBufferOverflow = BufferOverflow.DROP_OLDEST
  )

  private val documentListener = object : DocumentListener {
    override fun documentChanged(event: DocumentEvent) {
      updateRequests.tryEmit(Unit)
    }
  }

  init {
    updateRequests
      .debounce(300.milliseconds)
      .onEach { renderAndUpdatePreview() }
      .launchIn(coroutineScope)

    document?.addDocumentListener(documentListener, this)

    updateRequests.tryEmit(Unit)
  }

  private suspend fun renderAndUpdatePreview() {
    val text = readAction { document?.text }
    if (text.isNullOrBlank()) {
      showMessage("Empty file")
      return
    }

    val result = try {
      withContext(Dispatchers.Default) { renderPreview(text) }
    }
    catch (e: Exception) {
      RenderResult.Error("Unexpected error: ${e.message}")
    }

    when (result) {
      is RenderResult.Success -> showImage(result.image)
      is RenderResult.Error -> showMessage(result.message)
    }
  }

  private fun renderPreview(text: String): RenderResult {
    val dimension = VectorDrawableUtils.getSizeDp(text) ?: return RenderResult.Error("Invalid vector drawable: missing width/height")

    val targetSize = VdPreview.TargetSize.createFromMaxDimension(max(dimension.width, dimension.height))
    val errors = StringBuilder()
    val image = VdPreview.getPreviewFromVectorXml(targetSize, text, errors)

    return when {
      errors.isNotEmpty() -> RenderResult.Error(errors.toString())
      image == null -> RenderResult.Error("Failed to render preview")
      else -> RenderResult.Success(image)
    }
  }

  private suspend fun showImage(image: BufferedImage) = updatePanel(
    JBLabel(ImageIcon(image)).apply {
      horizontalAlignment = SwingConstants.CENTER
      verticalAlignment = SwingConstants.CENTER
    }
  )

  private suspend fun showMessage(message: String) = updatePanel(
    JBLabel(message, SwingConstants.CENTER).apply {
      border = JBUI.Borders.empty(10)
    }
  )

  private suspend fun updatePanel(component: JComponent) = withContext(Dispatchers.EDT) {
    panel.removeAll()
    panel.add(component, BorderLayout.CENTER)
    panel.revalidate()
    panel.repaint()
  }

  private sealed class RenderResult {
    data class Success(val image: BufferedImage) : RenderResult()
    data class Error(val message: String) : RenderResult()
  }

  override fun getComponent(): JComponent = panel
  override fun getPreferredFocusedComponent(): JComponent = panel
  override fun getName(): String = "Preview"
  override fun setState(state: FileEditorState) {}
  override fun isModified(): Boolean = false
  override fun isValid(): Boolean = file.isValid
  override fun addPropertyChangeListener(listener: PropertyChangeListener) {}
  override fun removePropertyChangeListener(listener: PropertyChangeListener) {}
  override fun getFile(): VirtualFile = file
  override fun dispose() {}
}