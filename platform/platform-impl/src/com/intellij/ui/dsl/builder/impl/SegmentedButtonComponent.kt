// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.dsl.builder.impl

import com.intellij.ide.ui.laf.darcula.DarculaUIUtil
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.actionSystem.Toggleable
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.actionSystem.impl.ActionButtonWithText
import com.intellij.openapi.actionSystem.impl.PresentationFactory
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.util.NlsActions
import com.intellij.ui.dsl.builder.DslComponentProperty
import com.intellij.ui.dsl.builder.SpacingConfiguration
import com.intellij.ui.dsl.builder.components.SegmentedButtonBorder
import com.intellij.ui.dsl.builder.components.SegmentedButtonLook
import com.intellij.ui.dsl.builder.components.getSegmentedButtonBorderPaint
import com.intellij.ui.dsl.gridLayout.Gaps
import com.intellij.ui.dsl.gridLayout.GridLayout
import com.intellij.ui.dsl.gridLayout.builders.RowsGridBuilder
import com.intellij.util.ui.JBInsets
import com.intellij.util.ui.JBUI
import org.jetbrains.annotations.ApiStatus
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.event.FocusEvent
import java.awt.event.FocusListener
import javax.swing.JPanel

private const val PLACE = "SegmentedButton"

@ApiStatus.Internal
internal class SegmentedButtonComponent<T>(options: Collection<T>, private val renderer: (T) -> String) : JPanel(GridLayout()) {

  var changeListener: (() -> Unit)? = null
  var spacing = SpacingConfiguration.EMPTY
    set(value) {
      field = value
      // Rebuild buttons with correct spacing
      options = _options
    }

  var selection: T?
    get() = _selection
    set(value) {
      fun setSelectedState(option: T?, selectedState: Boolean) {
        val componentIndex = options.indexOf(option)
        val segmentedButton = components.getOrNull(componentIndex) as? SegmentedButton<*>
        segmentedButton?.selectedState = selectedState
      }

      if (_selection != value) {
        setSelectedState(_selection, false)
        setSelectedState(value, true)
        _selection = value
        changeListener?.invoke()

        repaint()
      }
    }

  private var _selection: T? = null

  var options: Collection<T>
    get() = _options
    set(value) {
      removeAll()
      val presentationFactory = PresentationFactory()
      val builder = RowsGridBuilder(this)
      for (option in value) {
        val action = SegmentedButtonAction(this, option, renderer.invoke(option))
        val button = SegmentedButton(action, presentationFactory.getPresentation(action), spacing)
        builder.cell(button)
      }
      _options = value
    }

  private var _options: Collection<T> = emptyList()

  init {
    isFocusable = true
    border = SegmentedButtonBorder()
    putClientProperty(DslComponentProperty.VISUAL_PADDINGS, Gaps(size = DarculaUIUtil.BW.get()))

    this.options = options
    addFocusListener(object : FocusListener {
      override fun focusGained(e: FocusEvent?) {
        repaint()
      }

      override fun focusLost(e: FocusEvent?) {
        repaint()
      }
    })

    val actionLeft = DumbAwareAction.create { moveSelection(-1) }
    actionLeft.registerCustomShortcutSet(ActionUtil.getShortcutSet("SegmentedButton-left"), this)
    val actionRight = DumbAwareAction.create { moveSelection(1) }
    actionRight.registerCustomShortcutSet(ActionUtil.getShortcutSet("SegmentedButton-right"), this)
  }

  override fun setEnabled(enabled: Boolean) {
    super.setEnabled(enabled)

    for (component in components) {
      component.isEnabled = enabled
    }
  }

  override fun paint(g: Graphics) {
    super.paint(g)

    // Paint selected button frame over all children
    val g2 = g.create() as Graphics2D
    try {
      g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
      g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_NORMALIZE)
      g2.paint = getSegmentedButtonBorderPaint(this, true)
      val selectedButton = components.getOrNull(options.indexOf(selection))
      if (selectedButton != null) {
        val r = selectedButton.bounds
        JBInsets.addTo(r, JBUI.insets(DarculaUIUtil.LW.unscaled.toInt()))
        com.intellij.ui.dsl.builder.components.paintBorder(g2, r)
      }
    }
    finally {
      g2.dispose()
    }
  }

  private fun moveSelection(step: Int) {
    if (options.isEmpty()) {
      return
    }

    val selectedIndex = options.indexOf(selection)
    val newSelectedIndex = if (selectedIndex < 0) 0 else (selectedIndex + step).coerceIn(0, options.size - 1)
    selection = options.elementAt(newSelectedIndex)
  }
}

private class SegmentedButtonAction<T>(val parent: SegmentedButtonComponent<T>, val option: T, @NlsActions.ActionText optionText: String)
  : ToggleAction(optionText, null, null), DumbAware {

  override fun isSelected(e: AnActionEvent): Boolean {
    return parent.selection == option
  }

  override fun setSelected(e: AnActionEvent, state: Boolean) {
    if (state) {
      parent.selection = option
    }
  }
}

private class SegmentedButton<T>(
  private val action: SegmentedButtonAction<T>,
  presentation: Presentation,
  private val spacing: SpacingConfiguration
) : ActionButtonWithText(action, presentation, PLACE, Dimension(0, 0)) {

  var selectedState: Boolean
    get() = Toggleable.isSelected(myPresentation)
    set(value) {
      Toggleable.setSelected(myPresentation, value)
    }

  init {
    setLook(SegmentedButtonLook)
  }

  override fun getPreferredSize(): Dimension {
    val preferredSize = super.getPreferredSize()
    return Dimension(preferredSize.width + spacing.segmentedButtonHorizontalGap * 2,
                     preferredSize.height + spacing.segmentedButtonVerticalGap * 2)
  }

  override fun actionPerformed(event: AnActionEvent) {
    super.actionPerformed(event)

    // Restore toggle action if selected button pressed again
    selectedState = action.parent.selection == action.option
  }
}
