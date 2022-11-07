// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.dsl.builder.impl

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.ui.ComponentWithBrowseButton
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.text.TextWithMnemonic
import com.intellij.ui.RawCommandLineEditor
import com.intellij.ui.SearchTextField
import com.intellij.ui.TitledSeparator
import com.intellij.ui.ToolbarDecorator
import com.intellij.ui.dsl.UiDslException
import com.intellij.ui.dsl.builder.Cell
import com.intellij.ui.dsl.builder.DslComponentProperty
import com.intellij.ui.dsl.builder.HyperlinkEventAction
import com.intellij.ui.dsl.builder.SpacingConfiguration
import com.intellij.ui.dsl.builder.components.DslLabel
import com.intellij.ui.dsl.builder.components.DslLabelType
import com.intellij.ui.dsl.builder.components.SegmentedButtonComponent
import com.intellij.ui.dsl.builder.components.SegmentedButtonToolbar
import com.intellij.ui.dsl.gridLayout.Gaps
import com.intellij.ui.dsl.gridLayout.GridLayoutComponentProperty
import com.intellij.ui.dsl.gridLayout.toGaps
import org.jetbrains.annotations.ApiStatus
import javax.swing.*
import javax.swing.text.JTextComponent

/**
 * Internal component properties for UI DSL
 */
@ApiStatus.Internal
internal enum class DslComponentPropertyInternal {
  /**
   * A mark that component is a cell label, see [Cell.label]
   *
   * Value: true
   */
  CELL_LABEL
}

/**
 * [JPanel] descendants that should use default vertical gaps around similar to other standard components like labels, text fields etc
 */
// todo Use DslComponentProperty.TOP_BOTTOM_GAP instead
private val DEFAULT_VERTICAL_GAP_COMPONENTS = setOf(
  RawCommandLineEditor::class,
  SearchTextField::class,
  SegmentedButtonComponent::class,
  SegmentedButtonToolbar::class,
  TextFieldWithBrowseButton::class,
  TitledSeparator::class
)

/**
 * Throws exception instead of logging warning. Useful while forms building to avoid layout mistakes
 */
private const val FAIL_ON_WARN = false

private val LOG = Logger.getInstance("JetBrains UI DSL")

/**
 * Components that can have assigned labels
 */
private val ALLOWED_LABEL_COMPONENTS = listOf(
  JComboBox::class,
  JSlider::class,
  JSpinner::class,
  JTable::class,
  JTextComponent::class,
  JTree::class,
  SegmentedButtonComponent::class,
  SegmentedButtonToolbar::class
)

internal val JComponent.origin: JComponent
  get() {
    // todo Move into components implementation
    return when (this) {
      is TextFieldWithBrowseButton -> textField
      is ComponentWithBrowseButton<*> -> childComponent
      else -> {
        val interactiveComponent = getClientProperty(DslComponentProperty.INTERACTIVE_COMPONENT) as JComponent?
        interactiveComponent ?: this
      }
    }
  }

internal fun prepareVisualPaddings(component: JComponent): Gaps {
  var customVisualPaddings = component.getClientProperty(DslComponentProperty.VISUAL_PADDINGS) as? Gaps

  if (customVisualPaddings == null) {
    // todo Move into components implementation
    // Patch visual paddings for known components
    customVisualPaddings = when (component) {
      is RawCommandLineEditor -> component.editorField.insets.toGaps()
      is SearchTextField -> component.textEditor.insets.toGaps()
      is JScrollPane -> Gaps.EMPTY
      is ComponentWithBrowseButton<*> -> component.childComponent.insets.toGaps()
      else -> {
        if (component.getClientProperty(ToolbarDecorator.DECORATOR_KEY) != null) {
          Gaps.EMPTY
        }
        else {
          null
        }
      }
    }
  }

  if (customVisualPaddings == null) {
    return component.insets.toGaps()
  }
  component.putClientProperty(GridLayoutComponentProperty.SUB_GRID_AUTO_VISUAL_PADDINGS, false)
  return customVisualPaddings
}

internal fun getComponentGaps(left: Int, right: Int, component: JComponent, spacing: SpacingConfiguration): Gaps {
  val top = getDefaultVerticalGap(component, spacing)
  var bottom = top
  if (component.getClientProperty(DslComponentProperty.NO_BOTTOM_GAP) == true) {
    bottom = 0
  }
  return Gaps(top = top, left = left, bottom = bottom, right = right)
}

/**
 * Returns default top and bottom gap for [component]. All non [JPanel] components or
 * [DEFAULT_VERTICAL_GAP_COMPONENTS] have default vertical gap, zero otherwise
 */
internal fun getDefaultVerticalGap(component: JComponent, spacing: SpacingConfiguration): Int {
  val noDefaultVerticalGap = component is JPanel
                             && component.getClientProperty(ToolbarDecorator.DECORATOR_KEY) == null
                             && component.getClientProperty(DslComponentProperty.TOP_BOTTOM_GAP) != true
                             && !DEFAULT_VERTICAL_GAP_COMPONENTS.any { clazz -> clazz.isInstance(component) }

  return if (noDefaultVerticalGap) 0 else spacing.verticalComponentGap
}

internal fun createComment(@NlsContexts.Label text: String, maxLineLength: Int, action: HyperlinkEventAction): DslLabel {
  val result = DslLabel(DslLabelType.COMMENT)
  result.action = action
  result.maxLineLength = maxLineLength
  result.text = text
  return result
}

internal fun labelCell(label: JLabel, cell: CellBaseImpl<*>?) {
  val mnemonic = TextWithMnemonic.fromMnemonicText(label.text)
  val mnemonicExists = label.displayedMnemonic != 0 || label.displayedMnemonicIndex >= 0 || mnemonic?.hasMnemonic() == true
  if (cell !is CellImpl<*>) {
    if (mnemonicExists) {
      warn("Cannot assign mnemonic to Panel and other non-component cells, label '${label.text}'")
    }
    return
  }

  val component = getLabelComponentFor(cell.component.origin)
  if (component == null) {
    if (mnemonicExists) {
      warn("Unsupported labeled component ${cell.component.javaClass.name}, label '${label.text}'")
    }
    return
  }

  label.labelFor = component
}

private fun getLabelComponentFor(component: JComponent): JComponent? {
  val labelFor = component.getClientProperty(DslComponentProperty.LABEL_FOR)
  if (labelFor != null) {
    if (labelFor is JComponent) {
      return labelFor
    }
    else {
      throw UiDslException("LABEL_FOR must be a JComponent: ${labelFor::class.java.name}")
    }
  }

  if (ALLOWED_LABEL_COMPONENTS.any { clazz -> clazz.isInstance(component) }) {
    return component
  }
  return null
}

internal fun warn(message: String) {
  if (FAIL_ON_WARN) {
    throw UiDslException(message)
  }
  else {
    LOG.warn(message)
  }
}
