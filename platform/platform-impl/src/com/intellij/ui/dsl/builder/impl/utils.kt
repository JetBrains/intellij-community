// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.dsl.builder.impl

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.util.NlsContexts
import com.intellij.ui.dsl.UiDslException
import com.intellij.ui.dsl.builder.HyperlinkEventAction
import com.intellij.ui.dsl.builder.components.DslLabel
import com.intellij.ui.dsl.builder.components.DslLabelType
import org.jetbrains.annotations.ApiStatus
import javax.swing.*
import javax.swing.text.JTextComponent

/**
 * Internal component properties for UI DSL
 */
@ApiStatus.Internal
internal enum class DslComponentPropertyInternal {
  /**
   * Removes standard bottom gap from label
   */
  LABEL_NO_BOTTOM_GAP
}

/**
 * Throws exception instead of logging warning. Useful while forms building to avoid layout mistakes
 */
private const val FAIL_ON_WARN = false

private val LOG = Logger.getInstance("Jetbrains UI DSL")

/**
 * Components that can have assigned labels
 */
private val ALLOWED_LABEL_COMPONENTS = listOf(
  JComboBox::class,
  JSlider::class,
  JSpinner::class,
  JTextComponent::class
)

internal val JComponent.origin: JComponent
  get() {
    return when (this) {
      is TextFieldWithBrowseButton -> textField
      else -> this
    }
  }

internal fun createComment(@NlsContexts.Label text: String, maxLineLength: Int, action: HyperlinkEventAction): DslLabel {
  val result = DslLabel(DslLabelType.COMMENT)
  result.action = action
  result.setHtmlText(text, maxLineLength)
  return result
}

internal fun isAllowedLabel(cell: CellBaseImpl<*>?): Boolean {
  return cell is CellImpl<*> && ALLOWED_LABEL_COMPONENTS.any { clazz -> clazz.isInstance(cell.component.origin) }
}

internal fun labelCell(label: JLabel, cell: CellBaseImpl<*>?) {
  if (isAllowedLabel(cell)) {
    label.labelFor = (cell as CellImpl<*>).component.origin
  }
}

internal fun warn(message: String) {
  if (FAIL_ON_WARN) {
    throw UiDslException(message)
  }
  else {
    LOG.warn(message)
  }
}
