// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.dsl.builder.impl

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.util.NlsContexts
import com.intellij.ui.TitledSeparator
import com.intellij.ui.ToolbarDecorator
import com.intellij.ui.dsl.UiDslException
import com.intellij.ui.dsl.builder.Cell
import com.intellij.ui.dsl.builder.DslComponentProperty
import com.intellij.ui.dsl.builder.HyperlinkEventAction
import com.intellij.ui.dsl.builder.SpacingConfiguration
import com.intellij.ui.dsl.builder.components.DslLabel
import com.intellij.ui.dsl.builder.components.DslLabelType
import com.intellij.ui.dsl.builder.components.SegmentedButtonToolbar
import com.intellij.ui.dsl.gridLayout.Gaps
import com.intellij.ui.layout.*
import org.jetbrains.annotations.ApiStatus
import javax.swing.*
import javax.swing.text.JTextComponent
import kotlin.reflect.KMutableProperty0
import kotlin.reflect.jvm.isAccessible

/**
 * Internal component properties for UI DSL
 */
@ApiStatus.Internal
internal enum class DslComponentPropertyInternal {
  /**
   * Removes standard bottom gap from label
   */
  LABEL_NO_BOTTOM_GAP,

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
private val DEFAULT_VERTICAL_GAP_COMPONENTS = setOf(
  SegmentedButtonComponent::class,
  SegmentedButtonToolbar::class,
  TextFieldWithBrowseButton::class,
  TitledSeparator::class
)

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
  JTextComponent::class,
  SegmentedButtonComponent::class,
  SegmentedButtonToolbar::class
)

internal val JComponent.origin: JComponent
  get() {
    return when (this) {
      is TextFieldWithBrowseButton -> textField
      else -> this
    }
  }

internal fun getVisualPaddings(component: JComponent): Gaps {
  val insets = component.insets
  val customVisualPaddings = component.getClientProperty(DslComponentProperty.VISUAL_PADDINGS) as? Gaps
  return customVisualPaddings ?: Gaps(top = insets.top, left = insets.left, bottom = insets.bottom, right = insets.right)
}

internal fun getComponentGaps(left: Int, right: Int, component: JComponent, spacing: SpacingConfiguration): Gaps {
  val top = getDefaultVerticalGap(component, spacing)
  var bottom = top
  if (component is JLabel && component.getClientProperty(DslComponentPropertyInternal.LABEL_NO_BOTTOM_GAP) == true) {
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

@ApiStatus.Internal
inline fun <reified T : Any> KMutableProperty0<T>.toBindingInternal(): PropertyBinding<T> {
  return createPropertyBinding(this, T::class.javaPrimitiveType ?: T::class.java)
}

@ApiStatus.Internal
fun <T> createPropertyBinding(prop: KMutableProperty0<T>, propType: Class<T>): PropertyBinding<T> {
  /*
  // looks like this code is not needed. Should be removed together with propType paramater after dogfooding
  if (prop is CallableReference) {
    val name = prop.name
    val receiver = (prop as CallableReference).boundReceiver
    if (receiver != null) {
      val baseName = name.removePrefix("is")
      val nameCapitalized = StringUtil.capitalize(baseName)
      val getterName = if (name.startsWith("is")) name else "get$nameCapitalized"
      val setterName = "set$nameCapitalized"
      val receiverClass = receiver::class.java

      try {
        val getter = receiverClass.getMethod(getterName)
        val setter = receiverClass.getMethod(setterName, propType)
        return PropertyBinding({ getter.invoke(receiver) as T }, { setter.invoke(receiver, it) })
      }
      catch (e: Exception) {
        // ignore
      }

      try {
        val field = receiverClass.getDeclaredField(name)
        field.isAccessible = true
        return PropertyBinding({ field.get(receiver) as T }, { field.set(receiver, it) })
      }
      catch (e: Exception) {
        // ignore
      }
    }
  }
  */
  prop.getter.isAccessible = true
  prop.setter.isAccessible = true
  return PropertyBinding(prop.getter, prop.setter)
}
