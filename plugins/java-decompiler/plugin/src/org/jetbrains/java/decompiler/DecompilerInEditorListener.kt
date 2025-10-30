// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.java.decompiler

import com.intellij.ide.highlighter.JavaClassFileType
import com.intellij.openapi.actionSystem.ActionToolbar.DEFAULT_MINIMUM_BUTTON_SIZE
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.openapi.editor.event.EditorFactoryEvent
import com.intellij.openapi.editor.event.EditorFactoryListener
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.editor.impl.EditorInspectionsActionToolbar
import com.intellij.openapi.editor.impl.EditorMarkupModelImpl
import com.intellij.openapi.editor.impl.EditorToolbarButtonLook
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.panels.NonOpaquePanel
import com.intellij.util.ui.JBInsets
import com.intellij.util.ui.JBUI
import java.awt.Dimension
import java.util.function.Supplier
import javax.swing.BoxLayout
import kotlin.math.max

private class DecompilerInEditorListener : EditorFactoryListener {
  override fun editorCreated(event: EditorFactoryEvent) {
    val editor = event.editor as? EditorImpl ?: return
    val virtualFile = FileDocumentManager.getInstance().getFile(editor.document) ?: return
    if (virtualFile.fileType != JavaClassFileType.INSTANCE) return
    if (virtualFile.name == "module-info.class") return
    setupModeToggles(editor)
  }

  private fun setupModeToggles(editor: EditorImpl) {
    val decompilerSettings = IdeaDecompilerSettings.getInstance()

    val defaultActionGroup = DefaultActionGroup(
      DecompilerInEditorActionGroup(decompilerSettings),
    )

    val editorButtonLook = EditorToolbarButtonLook(editor)

    // We override this *only* to enforce the nice-looking minimum icon size.
    val statusToolbar = object : EditorInspectionsActionToolbar(defaultActionGroup, editor, editorButtonLook, null, null) {

      override fun canReuseActionButton(oldActionButton: ActionButton, newPresentation: Presentation) = true

      override fun createIconButton(action: AnAction, place: String, presentation: Presentation, minimumSize: Supplier<out Dimension>): ActionButton {
        return object : ToolbarActionButton(action, presentation, place, minimumSize) {
          override fun getPreferredSize(): Dimension {
            val size = Dimension(icon.iconWidth, icon.iconHeight)

            size.width = max(size.width, DEFAULT_MINIMUM_BUTTON_SIZE.width)
            size.height = max(size.height, DEFAULT_MINIMUM_BUTTON_SIZE.height)

            JBInsets.addTo(size, insets)
            return size
          }
        }
      }
    }

    statusToolbar.setMiniMode(true)
    statusToolbar.setCustomButtonLook(editorButtonLook)

    val toolbar = statusToolbar.component
    toolbar.layout = EditorMarkupModelImpl.StatusComponentLayout()
    toolbar.border = JBUI.Borders.empty(2)

    val statusPanel = NonOpaquePanel()
    statusPanel.isVisible = true
    statusPanel.layout = BoxLayout(statusPanel, BoxLayout.X_AXIS)
    statusPanel.add(toolbar)

    val scrollPane = editor.scrollPane as? JBScrollPane ?: return
    scrollPane.statusComponent = statusPanel

    val scrollPaneLayout = scrollPane.layout as? JBScrollPane.Layout ?: return
    scrollPaneLayout.syncWithScrollPane(scrollPane)
  }
}
