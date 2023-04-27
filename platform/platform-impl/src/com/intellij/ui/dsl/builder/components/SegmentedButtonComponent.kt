// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.dsl.builder.components

import com.intellij.ide.ui.laf.darcula.DarculaUIUtil
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.actionSystem.impl.ActionButtonWithText
import com.intellij.openapi.actionSystem.impl.PresentationFactory
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.observable.properties.ObservableMutableProperty
import com.intellij.openapi.observable.util.addMouseListener
import com.intellij.openapi.observable.util.lockOrSkip
import com.intellij.openapi.observable.util.whenDisposed
import com.intellij.openapi.observable.util.whenKeyReleased
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.util.NlsActions
import com.intellij.ui.dsl.builder.DslComponentProperty
import com.intellij.ui.dsl.builder.EmptySpacingConfiguration
import com.intellij.ui.dsl.builder.SpacingConfiguration
import com.intellij.ui.dsl.builder.VerticalComponentGap
import com.intellij.ui.dsl.gridLayout.GridLayout
import com.intellij.ui.dsl.gridLayout.HorizontalAlign
import com.intellij.ui.dsl.gridLayout.UnscaledGaps
import com.intellij.ui.dsl.gridLayout.builders.RowsGridBuilder
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.ui.JBInsets
import com.intellij.util.ui.JBUI
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import java.awt.*
import java.awt.event.FocusEvent
import java.awt.event.FocusListener
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.JPanel
import kotlin.math.roundToInt

private const val PLACE = "SegmentedButton"

internal val NO_TOOLTIP_RENDERER: (Any?) -> @Nls String? = { null }

@ApiStatus.Internal
internal class SegmentedButtonComponent<T>(items: Collection<T>,
                                           private val renderer: (T) -> @Nls String,
                                           private val tooltipRenderer: (T) -> @Nls String? = NO_TOOLTIP_RENDERER) : JPanel(GridLayout()) {

  var spacing: SpacingConfiguration = EmptySpacingConfiguration()
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
        for (listener in listenerList.getListeners(ModelListener::class.java)) {
          listener.onItemSelected()
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
    putClientProperty(DslComponentProperty.VISUAL_PADDINGS, UnscaledGaps(size = DarculaUIUtil.BW.unscaled.roundToInt()))
    putClientProperty(DslComponentProperty.VERTICAL_COMPONENT_GAP, VerticalComponentGap(true, true))

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

  fun addModelListener(l: ModelListener) {
    listenerList.add(ModelListener::class.java, l)
  }

  fun removeModelListener(l: ModelListener) {
    listenerList.remove(ModelListener::class.java, l)
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
        paintBorder(g2, r)
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
      button.toolTipText = tooltipRenderer.invoke(item)

      builder.cell(button, horizontalAlign = HorizontalAlign.FILL, resizableColumn = true)
    }

    for (listener in listenerList.getListeners(ModelListener::class.java)) {
      listener.onRebuild()
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

  companion object {

    fun <T> SegmentedButtonComponent<T>.bind(property: ObservableMutableProperty<T>) {
      val mutex = AtomicBoolean()
      property.afterChange {
        mutex.lockOrSkip {
          selectedItem = it
        }
      }
      whenItemSelected {
        mutex.lockOrSkip {
          property.set(it)
        }
      }
    }

    fun <T> SegmentedButtonComponent<T>.whenItemSelected(parentDisposable: Disposable? = null, listener: (T) -> Unit) {
      addModelListener(parentDisposable, object : ModelListener {
        override fun onItemSelected() {
          selectedItem?.let(listener)
        }
      })
    }

    private fun SegmentedButtonComponent<*>.whenRebuild(parentDisposable: Disposable?, listener: () -> Unit) {
      addModelListener(parentDisposable, object : ModelListener {
        override fun onRebuild() = listener()
      })
    }

    fun <T> SegmentedButtonComponent<T>.addModelListener(parentDisposable: Disposable? = null, listener: ModelListener) {
      addModelListener(listener)
      parentDisposable?.whenDisposed {
        removeModelListener(listener)
      }
    }

    @ApiStatus.Experimental
    fun <T> SegmentedButtonComponent<T>.whenItemSelectedFromUi(parentDisposable: Disposable? = null, listener: (T) -> Unit) {
      whenKeyReleased(parentDisposable) {
        fireItemSelectedFromUi(listener)
      }
      whenButtonsTouchedFromUi(parentDisposable) {
        fireItemSelectedFromUi(listener)
      }
      whenRebuild(parentDisposable) {
        whenButtonsTouchedFromUi(parentDisposable) {
          fireItemSelectedFromUi(listener)
        }
      }
    }

    private fun <T> SegmentedButtonComponent<T>.fireItemSelectedFromUi(listener: (T) -> Unit) {
      invokeLater(ModalityState.stateForComponent(this)) {
        selectedItem?.let(listener)
      }
    }

    private fun SegmentedButtonComponent<*>.whenButtonsTouchedFromUi(parentDisposable: Disposable?, listener: () -> Unit) {
      for (button in components) {
        whenButtonTouchedFromUi(button, parentDisposable, listener)
      }
    }

    private fun SegmentedButtonComponent<*>.whenButtonTouchedFromUi(
      button: Component,
      parentDisposable: Disposable?,
      listener: () -> Unit
    ) {
      val mouseListener = object : MouseAdapter() {
        override fun mouseReleased(e: MouseEvent) = listener()
      }
      button.addMouseListener(parentDisposable, mouseListener)
      whenRebuild(parentDisposable) {
        button.removeMouseListener(mouseListener)
      }
    }
  }

  @ApiStatus.Internal
  internal interface ModelListener : EventListener {
    fun onItemSelected() {}

    fun onRebuild() {}
  }
}

private class SegmentedButtonAction<T>(val parent: SegmentedButtonComponent<T>, val item: T, @NlsActions.ActionText itemText: String)
  : ToggleAction(itemText, null, null), DumbAware {

  override fun isSelected(e: AnActionEvent): Boolean {
    return parent.selectedItem == item
  }

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.EDT
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

  override fun setToolTipText(toolTipText: String?) {
    setCustomToolTipText(toolTipText)
  }

  override fun getPreferredSize(): Dimension {
    val preferredSize = super.getPreferredSize()
    return Dimension(preferredSize.width + JBUIScale.scale(spacing.segmentedButtonHorizontalGap) * 2,
                     preferredSize.height + JBUIScale.scale(spacing.segmentedButtonVerticalGap) * 2)
  }

  override fun actionPerformed(event: AnActionEvent) {
    super.actionPerformed(event)

    // Restore toggle action if selected button pressed again
    selectedState = action.parent.selectedItem == action.item
  }
}
