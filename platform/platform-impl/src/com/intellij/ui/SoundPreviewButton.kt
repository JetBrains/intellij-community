// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:ApiStatus.Experimental

package com.intellij.ui

import com.intellij.icons.AllIcons
import com.intellij.ide.IdeBundle
import com.intellij.ide.ui.laf.darcula.DarculaNewUIUtil
import com.intellij.ide.ui.laf.darcula.DarculaUIUtil
import com.intellij.openapi.actionSystem.ActionButtonComponent
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.openapi.actionSystem.impl.IdeaActionButtonLook
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.ui.dsl.builder.Cell
import com.intellij.ui.dsl.builder.DslComponentProperty
import com.intellij.ui.dsl.builder.Row
import com.intellij.ui.dsl.builder.VerticalComponentGap
import com.intellij.ui.dsl.builder.actionButton
import com.intellij.util.ui.JBInsets
import com.intellij.util.ui.JBUI
import org.jetbrains.annotations.ApiStatus
import java.awt.Graphics
import java.awt.Rectangle
import javax.swing.JComponent

fun Row.soundPreviewButton(preview: () -> Unit): Cell<ActionButton> =
  actionButton(PreviewSoundAction(preview))
    .applyToComponent {
      isFocusable = true
      border = JBUI.Borders.empty(DarculaUIUtil.BW.unscaled.toInt() + FOCUS_RING_GAP)
      setLook(PreviewButtonLook)
      putClientProperty(DslComponentProperty.VERTICAL_COMPONENT_GAP, VerticalComponentGap.NONE)
    }

private class PreviewSoundAction(private val preview: () -> Unit) :
  DumbAwareAction(IdeBundle.message("sound.preview.button"), null, AllIcons.Actions.PlaySound) {
  override fun actionPerformed(e: AnActionEvent) {
    preview()
  }
}

private const val FOCUS_RING_GAP = 1

private object PreviewButtonLook : IdeaActionButtonLook() {
  override fun paintBackground(g: Graphics, component: JComponent, state: Int) {
    if (state != ActionButtonComponent.SELECTED) super.paintBackground(g, component, state)
  }

  override fun paintBorder(g: Graphics, component: JComponent, state: Int) {
    if (!component.isFocusOwner) {
      super.paintBorder(g, component, state)
      return
    }
    val bw = DarculaUIUtil.BW.get()
    val gap = JBUI.scale(FOCUS_RING_GAP)
    val rect = Rectangle(component.size)
    JBInsets.removeFrom(rect, component.insets)
    rect.grow(gap + bw, gap + bw)
    val arc = buttonArc.float + 2 * (gap + bw)
    DarculaNewUIUtil.drawRoundedRectangle(g, rect, JBUI.CurrentTheme.Focus.focusColor(), arc, bw.toFloat())
  }
}
