// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.inlay

import com.intellij.openapi.actionSystem.CommonShortcuts
import com.intellij.openapi.editor.ComponentInlayAlignment
import com.intellij.openapi.editor.ComponentInlayRenderer
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.EditorKind
import com.intellij.openapi.editor.EditorSettings
import com.intellij.openapi.editor.InlayProperties
import com.intellij.openapi.editor.addComponentInlay
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.ex.util.EditorUtil
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.ui.JBUI
import org.jetbrains.annotations.ApiStatus
import javax.swing.JComponent

@ApiStatus.Internal
class EditorInlayBuilder internal constructor(private val hostEditor: Editor) {
  var alignment: ComponentInlayAlignment = ComponentInlayAlignment.FIT_VIEWPORT_X_SPAN

  var showAbove: Boolean = false
  var relatesToPrecedingText: Boolean = true
  var priority: Int = 0

  var closeOnEscape: Boolean = true

  private var content: JComponent? = null
  private var embeddedEditor: EditorEx? = null
  private var embeddedFile: VirtualFile? = null
  private var header: EditorInlayHeaderBuilder? = null
  private val style = EditorInlayStyleBuilder()

  fun editor(
    document: Document,
    file: VirtualFile? = null,
    viewer: Boolean = true,
    settings: EditorSettings.() -> Unit = {},
  ): EditorEx {
    check(content == null) { "Inlay content is already set" }
    val editorFactory = EditorFactory.getInstance()
    val editor = when {
      file != null -> editorFactory.createEditor(document, hostEditor.project, file, viewer, EditorKind.PREVIEW)
      viewer -> editorFactory.createViewer(document, hostEditor.project, EditorKind.PREVIEW)
      else -> editorFactory.createEditor(document, hostEditor.project, EditorKind.PREVIEW)
    } as EditorEx
    editor.setBorder(JBUI.Borders.empty())
    editor.settings.isAnimatedScrolling = false
    editor.settings.settings()
    embeddedEditor = editor
    embeddedFile = file
    content = editor.component
    return editor
  }

  fun header(configure: EditorInlayHeaderBuilder.() -> Unit = {}) {
    header = (header ?: EditorInlayHeaderBuilder()).apply(configure)
  }

  fun style(configure: EditorInlayStyleBuilder.() -> Unit) {
    style.configure()
  }

  internal fun build(offset: Int): EditorInlay? {
    val content = requireNotNull(content) { "Inlay content is not set: call editor()" }
    val style = style.build(hostEditor.colorsScheme.defaultBackground)
    val panel = EditorInlayPanel(style, header?.build(embeddedFile), content)
    val properties = InlayProperties()
      .relatesToPrecedingText(relatesToPrecedingText)
      .showAbove(showAbove)
      .priority(priority)
    val inlay = hostEditor.addComponentInlay(offset, properties, ComponentInlayRenderer<JComponent>(panel, alignment))
    if (inlay == null) {
      embeddedEditor?.let { EditorFactory.getInstance().releaseEditor(it) }
      return null
    }

    val editorInlay = EditorInlay(inlay, panel, embeddedEditor)
    panel.closeHandler = { Disposer.dispose(editorInlay) }
    EditorUtil.disposeWithEditor(hostEditor, editorInlay)
    if (closeOnEscape) {
      DumbAwareAction.create { Disposer.dispose(editorInlay) }
        .registerCustomShortcutSet(CommonShortcuts.ESCAPE, hostEditor.contentComponent, editorInlay)
    }
    panel.validate()
    return editorInlay
  }
}
