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
import java.util.*
import javax.swing.JPanel

private const val PLACE = "SegmentedButton"

@ApiStatus.Internal
internal class SegmentedButtonComponent<T>(items: Collection<T>, private val renderer: (T) -> String) : JPanel(GridLayout()) {


  companion object {
    @ApiStatus.Internal
    internal fun interface SelectedItemListener: EventListener {
      fun onChanged()
    }
  }

  var spacing = SpacingConfiguration.EMPTY
    set(value) {
      field = value
      // Rebuild buttons with correct spacing
      rebuild()
    }

  var selectedItem: T? = null
    set(value) {
      if (field != value) {
        setSelectedState(field, false)
        setSelectedState(value, true)
        field = value
        for (listener in listenerList.getListeners(SelectedItemListener::class.java)) {
          listener.onChanged()
        }

        repaint()
      }
    }

  var items: Collection<T> = emptyList()
    set(value) {
      field = value
      rebuild()
    }

  init {
    isFocusable = true
    border = SegmentedButtonBorder()
    putClientProperty(DslComponentProperty.VISUAL_PADDINGS, Gaps(size = DarculaUIUtil.BW.get()))

    this.items = items
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

  fun addSelectedItemListener(l: SelectedItemListener) {
    listenerList.add(SelectedItemListener::class.java, l)
  }

  fun removeSelectedItemListener(l: SelectedItemListener) {
    listenerList.remove(SelectedItemListener::class.java, l)
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
      val selectedButton = components.getOrNull(items.indexOf(selectedItem))
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

  private fun rebuild() {
    removeAll()
    val presentationFactory = PresentationFactory()
    val builder = RowsGridBuilder(this)
    for (item in items) {
      val action = SegmentedButtonAction(this, item, renderer.invoke(item))
      val button = SegmentedButton(action, presentationFactory.getPresentation(action), spacing)
      builder.cell(button)
    }
  }

  private fun setSelectedState(item: T?, selectedState: Boolean) {
    val componentIndex = items.indexOf(item)
    val segmentedButton = components.getOrNull(componentIndex) as? SegmentedButton<*>
    segmentedButton?.selectedState = selectedState
  }

  private fun moveSelection(step: Int) {
    if (items.isEmpty()) {
      return
    }

    val selectedIndex = items.indexOf(selectedItem)
    val newSelectedIndex = if (selectedIndex < 0) 0 else (selectedIndex + step).coerceIn(0, items.size - 1)
    selectedItem = items.elementAt(newSelectedIndex)
  }
}

private class SegmentedButtonAction<T>(val parent: SegmentedButtonComponent<T>, val item: T, @NlsActions.ActionText itemText: String)
  : ToggleAction(itemText, null, null), DumbAware {

  override fun isSelected(e: AnActionEvent): Boolean {
    return parent.selectedItem == item
  }

  override fun setSelected(e: AnActionEvent, state: Boolean) {
    if (state) {
      parent.selectedItem = item
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
    selectedState = action.parent.selectedItem == action.item
  }
}
