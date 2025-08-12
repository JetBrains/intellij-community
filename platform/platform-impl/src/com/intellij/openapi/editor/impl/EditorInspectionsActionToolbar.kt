// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl

import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.ex.ActionButtonLook
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.openapi.actionSystem.impl.ActionButtonWithText
import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl
import com.intellij.openapi.editor.impl.inspector.RedesignedInspectionsManager
import com.intellij.ui.ClientProperty
import com.intellij.ui.JBColor
import com.intellij.util.ui.JBInsets
import com.intellij.util.ui.JBUI
import org.jetbrains.annotations.ApiStatus
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Insets
import java.util.function.Supplier
import kotlin.math.max

@ApiStatus.Internal
open class EditorInspectionsActionToolbar(
  actions: DefaultActionGroup,
  private val editor: EditorImpl,
  private val editorButtonLook: ActionButtonLook,
  private val nextErrorAction: AnAction?,
  private val prevErrorAction: AnAction?,
) : ActionToolbarImpl(ActionPlaces.EDITOR_INSPECTIONS_TOOLBAR, actions, true) {
  init {
    ClientProperty.put(this, SUPPRESS_FAST_TRACK, true)
  }

  override fun addNotify() {
    setTargetComponent(editor.contentComponent)
    super.addNotify()
  }

  override fun paintComponent(g: Graphics) {
    editorButtonLook.paintBackground(g, this, editor.backgroundColor)
  }

  override fun getSeparatorHeight(): Int = EditorMarkupModelImpl.statusIconSize

  override fun createTextButton(
    action: AnAction,
    place: String,
    presentation: Presentation,
    minimumSize: Supplier<out Dimension>,
  ): ActionButtonWithText {
    if (RedesignedInspectionsManager.isAvailable()) {
      return super.createTextButton(action, place, presentation, minimumSize)
    }

    val button = super.createTextButton(action, place, presentation, minimumSize)
    val color = JBColor.lazy { (editor.colorsScheme.getColor(EditorMarkupModelImpl.ICON_TEXT_COLOR)) ?: EditorMarkupModelImpl.ICON_TEXT_COLOR.defaultColor }
    button.setForeground(color)
    return button
  }

  override fun createIconButton(
    action: AnAction,
    place: String,
    presentation: Presentation,
    minimumSize: Supplier<out Dimension>,
  ): ActionButton {
    if (RedesignedInspectionsManager.isAvailable()) {
      return super.createIconButton(action, place, presentation, minimumSize)
    }
    return ToolbarActionButton(action, presentation, place, minimumSize)
  }

  override fun isDefaultActionButtonImplementation(oldActionButton: ActionButton, newPresentation: Presentation): Boolean {
    if (RedesignedInspectionsManager.isAvailable()) {
      return super.isDefaultActionButtonImplementation(oldActionButton, newPresentation)
    }
    return oldActionButton.javaClass == ToolbarActionButton::class.java
  }

  override fun doLayout() {
    val layoutManager = layout
    if (layoutManager != null) {
      layoutManager.layoutContainer(this)
    }
    else {
      super.doLayout()
    }
  }

  @ApiStatus.Internal
  open inner class ToolbarActionButton(
    action: AnAction,
    presentation: Presentation,
    place: String,
    minimumSize: Supplier<out Dimension>,
  ) : ActionButton(action, presentation, place, minimumSize) {
    override fun updateIcon() {
      super.updateIcon()
      revalidate()
      repaint()
    }

    override fun getInsets(): Insets {
      return when {
        myAction === nextErrorAction -> JBUI.insets(2, 1)
        myAction === prevErrorAction -> JBUI.insets(2, 1, 2, 2)
        else -> JBUI.insets(2)
      }
    }

    override fun getPreferredSize(): Dimension {
      val icon = getIcon()
      val size = Dimension(icon.iconWidth, icon.iconHeight)

      val minSize: Int = EditorMarkupModelImpl.statusIconSize
      size.width = max(size.width, minSize)
      size.height = max(size.height, minSize)

      JBInsets.addTo(size, insets)
      return size
    }
  }
}