// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:Suppress("ReplaceIsEmptyWithIfEmpty")

package com.intellij.ui.dsl.builder.impl

import com.intellij.BundleBase
import com.intellij.ide.TooltipTitle
import com.intellij.ide.ui.laf.darcula.ui.DarculaScrollPaneBorder
import com.intellij.internal.inspector.UiInspectorUtil
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.IntellijInternalApi
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.util.text.TextWithMnemonic
import com.intellij.ui.ContextHelpLabel
import com.intellij.ui.EditorTextField
import com.intellij.ui.dsl.UiDslException
import com.intellij.ui.dsl.builder.Cell
import com.intellij.ui.dsl.builder.DslComponentProperty
import com.intellij.ui.dsl.builder.HyperlinkEventAction
import com.intellij.ui.dsl.builder.MAX_LINE_LENGTH_WORD_WRAP
import com.intellij.ui.dsl.builder.Row
import com.intellij.ui.dsl.builder.SpacingConfiguration
import com.intellij.ui.dsl.builder.VerticalComponentGap
import com.intellij.ui.dsl.builder.components.DslLabel
import com.intellij.ui.dsl.builder.components.DslLabelType
import com.intellij.ui.dsl.builder.components.SegmentedButtonComponent
import com.intellij.ui.dsl.gridLayout.Gaps
import com.intellij.ui.dsl.gridLayout.GridLayoutComponentProperty
import com.intellij.ui.dsl.gridLayout.UnscaledGaps
import com.intellij.ui.dsl.gridLayout.toUnscaled
import com.intellij.ui.dsl.gridLayout.toUnscaledGaps
import com.intellij.util.IconUtil
import com.intellij.util.ui.accessibility.AccessibleContextUtil
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import javax.accessibility.AccessibleContext
import javax.swing.JCheckBox
import javax.swing.JComboBox
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JRadioButton
import javax.swing.JScrollPane
import javax.swing.JSlider
import javax.swing.JSpinner
import javax.swing.JTable
import javax.swing.JTree
import javax.swing.text.JTextComponent

/**
 * Internal component properties for UI DSL
 */
@ApiStatus.Internal
enum class DslComponentPropertyInternal {
  /**
   * A mark that component is a cell label, see [Cell.label]
   *
   * Value: true
   */
  CELL_LABEL,

  /**
   * Range of allowed integer values in text fields
   *
   * Value: IntRange
   */
  INT_TEXT_RANGE,

  /**
   * Place where the component was added into Kotlin UI DSL builder (for example [Row.cell]). Used in internal mode only
   *
   * Value: Throwable
   */
  CREATION_STACKTRACE,

  /**
   * Preferred columns width for DslLabel when [MAX_LINE_LENGTH_WORD_WRAP] mode is used.
   * A temporary workaround of IJPL-62164 will be removed later.
   *
   * Value: Int
   */
  @ApiStatus.Experimental
  @Deprecated("Not needed anymore, because IJPL-62164 has been implemented")
  @ApiStatus.ScheduledForRemoval
  PREFERRED_COLUMNS_LABEL_WORD_WRAP
}

/**
 * Throws exception instead of logging warning. Useful while forms building to avoid layout mistakes
 */
private const val FAIL_ON_WARN = false

private val LOG = Logger.getInstance("JetBrains UI DSL")

/**
 * Components that can have assigned labels
 */
private val ALLOWED_LABEL_COMPONENTS = listOf(
  JCheckBox::class,
  JComboBox::class,
  JRadioButton::class,
  JSlider::class,
  JSpinner::class,
  JTable::class,
  JTextComponent::class,
  JTree::class,
  SegmentedButtonComponent::class,
  EditorTextField::class
)

/**
 * See [DslComponentProperty.INTERACTIVE_COMPONENT]
 */
val JComponent.interactiveComponent: JComponent
  @ApiStatus.Internal
  get() {
    val interactiveComponent = getClientProperty(DslComponentProperty.INTERACTIVE_COMPONENT) as JComponent?
    return interactiveComponent ?: this
  }

@get:ApiStatus.Internal
@Deprecated("Quick workaround. Don't use this method, get rid of <html> instead")
val String?.trimHtml: String?
  get() = this?.removeSurrounding("<html>", "</html>")

internal fun prepareVisualPaddings(component: JComponent): UnscaledGaps {
  var customVisualPaddings: UnscaledGaps? =
    when (val value = component.getClientProperty(DslComponentProperty.VISUAL_PADDINGS)) {
      null -> null
      is Gaps -> value.toUnscaled()
      is UnscaledGaps -> value
      else -> throw UiDslException("Invalid VISUAL_PADDINGS")
    }

  if (customVisualPaddings == null && component is JScrollPane) {
    val visualPadding = (component.border as? DarculaScrollPaneBorder)?.getVisualPadding(component) ?: 0
    customVisualPaddings = UnscaledGaps(visualPadding)
  }

  if (customVisualPaddings == null) {
    return component.insets.toUnscaledGaps()
  }
  component.putClientProperty(GridLayoutComponentProperty.SUB_GRID_AUTO_VISUAL_PADDINGS, false)
  return customVisualPaddings
}

internal fun getComponentGaps(left: Int, right: Int, component: JComponent, spacing: SpacingConfiguration): UnscaledGaps {
  val defaultVerticalGap = if (component is JPanel) 0 else spacing.verticalComponentGap
  val policy = component.getClientProperty(DslComponentProperty.VERTICAL_COMPONENT_GAP) as VerticalComponentGap?
  return UnscaledGaps(top = calculateVerticalGap(defaultVerticalGap, spacing, policy?.top), left = left,
                      bottom = calculateVerticalGap(defaultVerticalGap, spacing, policy?.bottom), right = right)
}

private fun calculateVerticalGap(defaultVerticalGap: Int, spacing: SpacingConfiguration, policy: Boolean?): Int {
  return when (policy) {
    true -> spacing.verticalComponentGap
    false -> 0
    null -> defaultVerticalGap
  }
}

internal fun createComment(@NlsContexts.Label text: String, maxLineLength: Int, action: HyperlinkEventAction): DslLabel {
  val result = DslLabel(DslLabelType.COMMENT)
  result.action = action
  result.maxLineLength = maxLineLength
  result.limitPreferredSize = maxLineLength == MAX_LINE_LENGTH_WORD_WRAP
  result.text = text
  return result
}

internal fun createContextHelp(description: @NlsContexts.Tooltip String, title: @TooltipTitle String?): ContextHelpLabel {
  val result = if (title == null) ContextHelpLabel.create(description)
  else ContextHelpLabel.create(title, description)

  result.putClientProperty(AccessibleContext.ACCESSIBLE_NAME_PROPERTY, AccessibleContextUtil.combineAccessibleStrings(
    title?.stripHtml(), "\n", description.stripHtml()))

  // Do not hide the context help button in the disabled state
  result.disabledIcon = IconUtil.desaturate(result.icon)
  return result
}

internal fun String.stripHtml(): @Nls String? {
  @Suppress("HardCodedStringLiteral")
  val result = StringUtil.stripHtml(this, " ").trim()
  return if (result.isEmpty()) null else result
}

internal fun labelCell(label: JLabel, cell: CellBaseImpl<*>?) {
  if (cell is PlaceholderBaseImpl) {
    cell.initLabelFor(label)
    return
  }

  val mnemonic = TextWithMnemonic.fromMnemonicText(label.text, true)
  val mnemonicExists = label.displayedMnemonic != 0 || label.displayedMnemonicIndex >= 0 || mnemonic?.hasMnemonic() == true
  if (cell !is CellImpl<*>) {
    if (mnemonicExists) {
      warn("Cannot assign mnemonic to Panel and other non-component cells, label '${label.text}'")
    }
    return
  }

  if (cell.component.getClientProperty(DslComponentProperty.SKIP_LABEL_FOR_ASSIGNMENT) as Boolean? == true) {
    return
  }

  val component = getLabelComponentFor(cell.component.interactiveComponent)
  if (component == null) {
    if (mnemonicExists) {
      warn("Unsupported labeled component ${cell.component.javaClass.name}, label '${label.text}'")
    }
    return
  }

  label.labelFor = component
}

internal fun createLabel(@NlsContexts.Label text: String): JLabel {
  // Old version supported \n, but it looks nobody needs that
  return JLabel(BundleBase.replaceMnemonicAmpersand(text))
}

private fun getLabelComponentFor(component: JComponent): JComponent? {
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

@OptIn(IntellijInternalApi::class)
internal fun registerCreationStacktrace(component: JComponent) {
  if (ApplicationManager.getApplication()?.isInternal == true && UiInspectorUtil.isSaveStacktraces()) {
    component.putClientProperty(DslComponentPropertyInternal.CREATION_STACKTRACE, Throwable())
  }
}

/**
 * Denied content and reasons
 */
private val DENIED_TAGS = mapOf(
  Regex("<html>", RegexOption.IGNORE_CASE) to "tag <html> inserted automatically and shouldn't be used",
  Regex("<body>", RegexOption.IGNORE_CASE) to "tag <body> inserted automatically and shouldn't be used",
  Regex("""<a\s+href\s*=\s*(""|'')\s*>""", RegexOption.IGNORE_CASE) to "empty href like <a href=''> is denied, use <a> instead",
)

@ApiStatus.Internal
fun checkDeniedHtmlTags(text: String) {
  for ((regex, reason) in DENIED_TAGS) {
    if (regex.find(text, 0) != null) {
      UiDslException.error("Invalid html: $reason, text: $text")
    }
  }
}
