// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.java.decompiler

import com.intellij.ide.highlighter.JavaClassFileType
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.openapi.editor.event.EditorFactoryEvent
import com.intellij.openapi.editor.event.EditorFactoryListener
import com.intellij.openapi.editor.ex.EditorMarkupModel
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.editor.impl.EditorMarkupModelImpl
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.panels.NonOpaquePanel
import com.intellij.util.ui.JBInsets
import com.intellij.util.ui.JBUI
import java.awt.Dimension
import java.util.function.Supplier
import javax.swing.BoxLayout
import kotlin.math.max

internal class DecompilerInEditorListener : EditorFactoryListener {
  override fun editorCreated(event: EditorFactoryEvent) {
    val editor = event.editor as? EditorImpl ?: return
    val file = editor.virtualFile ?: return
    if (file.fileType != JavaClassFileType.INSTANCE) return
    setupModeToggles(editor)
  }

  /**
   * We want to add an icon to the small toolbar in the upper-right corner of the editor (aka "the inspection widget").
   *
   * Normal, well-behaved plugins (for example GitHub plugin, for code review), contribute their own actions to the
   * inspection widget by calling [EditorMarkupModel.addInspectionWidgetAction].
   *
   * Unfortunately, we cannot act like a well-behaved plugin because the inspection widget is not shown for
   * class files (and other binary artifacts). This assumption is hardcoded quite deep in platform code.
   *
   * Therefore, we replace the whole "status component" (which itself is a part of [JBScrollPane]).
   *
   * We assume we will not overwrite any existing UI that was already there – because there wasn't any UI to be
   * overwritten – recall that the whole "status component" is not displayed for class files (and other binary artifacts).
   */
  private fun setupModeToggles(editor: EditorImpl) {
    val decompilerSettings = IdeaDecompilerSettings.getInstance()

    val defaultActionGroup = DefaultActionGroup(
      DecompilerInEditorActionGroup(decompilerSettings),
    )

    val editorButtonLook = EditorMarkupModelImpl.EditorToolbarButtonLook(editor)

    // We override this *only* to enforce the nice-looking minimum icon size.
    val statusToolbar = object : EditorMarkupModelImpl.EditorInspectionsActionToolbar(defaultActionGroup, editor, editorButtonLook, null, null) {
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
    toolbar.setBorder(JBUI.Borders.empty(2))

    val statusPanel = NonOpaquePanel()
    statusPanel.isVisible = true
    statusPanel.setLayout(BoxLayout(statusPanel, BoxLayout.X_AXIS))
    statusPanel.add(toolbar)

    val scrollPane = (editor.scrollPane as? JBScrollPane) ?: return
    scrollPane.statusComponent = statusPanel

    val scrollPaneLayout = (scrollPane.layout as? JBScrollPane.Layout) ?: return
    scrollPaneLayout.syncWithScrollPane(scrollPane)
  }
}
