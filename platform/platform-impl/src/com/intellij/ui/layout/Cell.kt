// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("DEPRECATION")

package com.intellij.ui.layout

import com.intellij.BundleBase
import com.intellij.openapi.observable.properties.GraphProperty
import com.intellij.openapi.observable.util.bind
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.ui.panel.ComponentPanelBuilder
import com.intellij.openapi.util.NlsContexts.*
import com.intellij.openapi.util.text.StringUtil
import com.intellij.ui.JBIntSpinner
import com.intellij.ui.SimpleListCellRenderer
import com.intellij.ui.components.*
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.UIUtil
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import java.awt.Component
import java.awt.Dimension
import java.awt.event.ActionEvent
import javax.swing.*
import javax.swing.text.JTextComponent
import kotlin.jvm.internal.CallableReference
import kotlin.reflect.KMutableProperty0

@DslMarker
@ApiStatus.ScheduledForRemoval
@Deprecated("Use Kotlin UI DSL Version 2")
annotation class CellMarker

@ApiStatus.ScheduledForRemoval
@Deprecated("Use Kotlin UI DSL Version 2 and MutableProperty")
data class PropertyBinding<V>(val get: () -> V, val set: (V) -> Unit)

@PublishedApi
@ApiStatus.ScheduledForRemoval
@Deprecated("Use Kotlin UI DSL Version 2")
internal fun <T> createPropertyBinding(prop: KMutableProperty0<T>, propType: Class<T>): PropertyBinding<T> {
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
  return PropertyBinding(prop.getter, prop.setter)
}

@ApiStatus.ScheduledForRemoval
@Deprecated("Use MutableProperty and Kotlin UI DSL 2", level = DeprecationLevel.HIDDEN)
fun <T> PropertyBinding<T>.toNullable(): PropertyBinding<T?> {
  return PropertyBinding({ get() }, { set(it!!) })
}

private fun <T> PropertyBinding<T>.intToNullable(): PropertyBinding<T?> {
  return PropertyBinding({ get() }, { set(it!!) })
}

@ApiStatus.ScheduledForRemoval
@Deprecated("Use Kotlin UI DSL Version 2", level = DeprecationLevel.HIDDEN)
inline fun <reified T : Any> KMutableProperty0<T>.toBinding(): PropertyBinding<T> {
  return createPropertyBinding(this, T::class.javaPrimitiveType ?: T::class.java)
}

inline fun <reified T : Any> KMutableProperty0<T>.intToBinding(): PropertyBinding<T> {
  return createPropertyBinding(this, T::class.javaPrimitiveType ?: T::class.java)
}

class ValidationInfoBuilder(val component: JComponent) {
  fun error(@DialogMessage message: String): ValidationInfo = ValidationInfo(message, component)
  fun warning(@DialogMessage message: String): ValidationInfo = ValidationInfo(message, component).asWarning().withOKEnabled()
}

@JvmDefaultWithCompatibility
@ApiStatus.ScheduledForRemoval
@Deprecated("Use Kotlin UI DSL Version 2")
interface CellBuilder<out T : JComponent> {
  @get:Deprecated("Use Kotlin UI DSL Version 2")
  @get:ApiStatus.ScheduledForRemoval
  val component: T

  @ApiStatus.ScheduledForRemoval
  @Deprecated("Use Kotlin UI DSL 2")
  fun comment(@DetailedDescription text: String, maxLineLength: Int = ComponentPanelBuilder.MAX_COMMENT_WIDTH,
              forComponent: Boolean = false): CellBuilder<T>

  @ApiStatus.ScheduledForRemoval
  @Deprecated("Use Kotlin UI DSL Version 2", level = DeprecationLevel.HIDDEN)
  fun focused(): CellBuilder<T>

  @ApiStatus.ScheduledForRemoval
  @Deprecated("Use Kotlin UI DSL Version 2", level = DeprecationLevel.HIDDEN)
  fun withValidationOnApply(callback: ValidationInfoBuilder.(T) -> ValidationInfo?): CellBuilder<T>

  @ApiStatus.ScheduledForRemoval
  @Deprecated("Use Kotlin UI DSL Version 2", level = DeprecationLevel.HIDDEN)
  fun withValidationOnInput(callback: ValidationInfoBuilder.(T) -> ValidationInfo?): CellBuilder<T>

  @ApiStatus.ScheduledForRemoval
  @Deprecated("Use Kotlin UI DSL Version 2")
  fun onApply(callback: () -> Unit): CellBuilder<T>

  @ApiStatus.ScheduledForRemoval
  @Deprecated("Use Kotlin UI DSL Version 2")
  fun onReset(callback: () -> Unit): CellBuilder<T>

  @ApiStatus.ScheduledForRemoval
  @Deprecated("Use Kotlin UI DSL Version 2")
  fun onIsModified(callback: () -> Boolean): CellBuilder<T>

  @ApiStatus.ScheduledForRemoval
  @Deprecated("Use Kotlin UI DSL Version 2")
  fun growPolicy(growPolicy: GrowPolicy): CellBuilder<T>

  @ApiStatus.ScheduledForRemoval
  @Deprecated("Use Kotlin UI DSL Version 2")
  fun constraints(vararg constraints: CCFlags): CellBuilder<T>

  /**
   * If this method is called, the value of the component will be stored to the backing property only if the component is enabled.
   */
  @ApiStatus.ScheduledForRemoval
  @Deprecated("Use Kotlin UI DSL Version 2", level = DeprecationLevel.HIDDEN)
  fun applyIfEnabled(): CellBuilder<T>

  @ApiStatus.ScheduledForRemoval
  @Deprecated("Use Kotlin UI DSL Version 2", level = DeprecationLevel.HIDDEN)
  fun <V> withBinding(
    componentGet: (T) -> V,
    componentSet: (T, V) -> Unit,
    modelBinding: PropertyBinding<V>
  ): CellBuilder<T> {
    onApply { if (shouldSaveOnApply()) modelBinding.set(componentGet(component)) }
    onReset { componentSet(component, modelBinding.get()) }
    onIsModified { shouldSaveOnApply() && componentGet(component) != modelBinding.get() }
    return this
  }

  @ApiStatus.ScheduledForRemoval
  @ApiStatus.Internal
  @Deprecated("Use Kotlin UI DSL Version 2")
  fun <V> withBindingInt(
    componentGet: (T) -> V,
    componentSet: (T, V) -> Unit,
    modelBinding: PropertyBinding<V>
  ): CellBuilder<T> {
    onApply { if (shouldSaveOnApply()) modelBinding.set(componentGet(component)) }
    onReset { componentSet(component, modelBinding.get()) }
    onIsModified { shouldSaveOnApply() && componentGet(component) != modelBinding.get() }
    return this
  }

  @ApiStatus.ScheduledForRemoval
  @Deprecated("Use Kotlin UI DSL Version 2")
  fun withGraphProperty(property: GraphProperty<*>): CellBuilder<T>

  @ApiStatus.ScheduledForRemoval
  @Deprecated("Use Kotlin UI DSL Version 2", level = DeprecationLevel.HIDDEN)
  fun enabled(isEnabled: Boolean)

  @ApiStatus.ScheduledForRemoval
  @Deprecated("Use Kotlin UI DSL Version 2", level = DeprecationLevel.HIDDEN)
  fun enableIf(predicate: ComponentPredicate): CellBuilder<T>

  @ApiStatus.ScheduledForRemoval
  @Deprecated("Use Kotlin UI DSL Version 2", level = DeprecationLevel.HIDDEN)
  fun visibleIf(predicate: ComponentPredicate): CellBuilder<T>

  @Deprecated("Use Kotlin UI DSL Version 2")
  @ApiStatus.Internal
  fun shouldSaveOnApply(): Boolean

  @ApiStatus.ScheduledForRemoval
  @Deprecated("Use Kotlin UI DSL Version 2", level = DeprecationLevel.HIDDEN)
  fun withLargeLeftGap(): CellBuilder<T>
}

@ApiStatus.ScheduledForRemoval
@Deprecated("Use Kotlin UI DSL Version 2", level = DeprecationLevel.HIDDEN)
fun <T : JComponent> CellBuilder<T>.applyToComponent(task: T.() -> Unit): CellBuilder<T> {
  return also { task(component) }
}

private fun <T : JComponent> CellBuilder<T>.intApplyToComponent(task: T.() -> Unit): CellBuilder<T> {
  return also { task(component) }
}

@ApiStatus.ScheduledForRemoval
@Deprecated("Use Kotlin UI DSL Version 2", level = DeprecationLevel.HIDDEN)
fun <T : JTextComponent> CellBuilder<T>.withTextBinding(modelBinding: PropertyBinding<String>): CellBuilder<T> {
  return withBindingInt(JTextComponent::getText, JTextComponent::setText, modelBinding)
}

@get:Deprecated("Use Kotlin UI DSL Version 2", level = DeprecationLevel.HIDDEN)
@get:ApiStatus.ScheduledForRemoval
@Deprecated("Use Kotlin UI DSL Version 2", level = DeprecationLevel.HIDDEN)
val CellBuilder<AbstractButton>.selected: ComponentPredicate
  get() = component.selected

// separate class to avoid row related methods in the `cell { } `
@CellMarker
@ApiStatus.ScheduledForRemoval
@Deprecated("Use Kotlin UI DSL Version 2")
abstract class Cell : BaseBuilder {
  /**
   * Sets how keen the component should be to grow in relation to other component **in the same cell**. Use `push` in addition if need.
   * If this constraint is not set the grow weight is set to 0 and the component will not grow (unless some automatic rule is not applied.
   * Grow weight will only be compared against the weights for the same cell.
   */
  @ApiStatus.ScheduledForRemoval
  @Deprecated("Use Kotlin UI DSL Version 2", level = DeprecationLevel.HIDDEN)
  val growX: CCFlags = CCFlags.growX

  @ApiStatus.ScheduledForRemoval
  @Deprecated("Use Kotlin UI DSL Version 2", level = DeprecationLevel.HIDDEN)
  val grow: CCFlags = CCFlags.grow

  /**
   * Makes the row that the component is residing in grow with `weight`.
   */
  @ApiStatus.ScheduledForRemoval
  @Deprecated("Use Kotlin UI DSL Version 2", level = DeprecationLevel.HIDDEN)
  val pushY: CCFlags = CCFlags.pushY

  @ApiStatus.ScheduledForRemoval
  @Deprecated("Use Kotlin UI DSL Version 2")
  @ApiStatus.Internal
  fun label(@Label text: String,
            style: UIUtil.ComponentStyle? = null,
            fontColor: UIUtil.FontColor? = null,
            bold: Boolean = false): CellBuilder<JLabel> {
    val label = Label(text, style, fontColor, bold)
    return component(label)
  }

  @ApiStatus.ScheduledForRemoval
  @Deprecated("Use Kotlin UI DSL Version 2", level = DeprecationLevel.HIDDEN)
  fun label(@Label text: String,
            font: JBFont,
            fontColor: UIUtil.FontColor? = null): CellBuilder<JLabel> {
    val label = Label(text, fontColor = fontColor, font = font)
    return component(label)
  }

  @ApiStatus.ScheduledForRemoval
  @Deprecated("Use Kotlin UI DSL Version 2", level = DeprecationLevel.HIDDEN)
  fun link(text: @LinkLabel String,
           style: UIUtil.ComponentStyle? = null,
           action: () -> Unit): CellBuilder<JComponent> {
    val result = Link(text, style, action)
    return component(result)
  }

  @ApiStatus.ScheduledForRemoval
  @Deprecated("Use Kotlin UI DSL Version 2", level = DeprecationLevel.HIDDEN)
  fun browserLink(text: @LinkLabel String, url: String): CellBuilder<JComponent> {
    val result = BrowserLink(text, url)
    return component(result)
  }

  @ApiStatus.ScheduledForRemoval
  @Deprecated("Use Kotlin UI DSL Version 2", level = DeprecationLevel.HIDDEN)
  fun button(text: @Button String, actionListener: (event: ActionEvent) -> Unit): CellBuilder<JButton> {
    val button = JButton(BundleBase.replaceMnemonicAmpersand(text))
    button.addActionListener(actionListener)
    return component(button)
  }

  @JvmOverloads
  @ApiStatus.ScheduledForRemoval
  @Deprecated("Use Kotlin UI DSL Version 2")
  fun checkBox(@Checkbox text: String,
               isSelected: Boolean = false,
               @DetailedDescription comment: String? = null): CellBuilder<JBCheckBox> {
    val result = JBCheckBox(text, isSelected)
    return result(comment = comment)
  }

  @ApiStatus.ScheduledForRemoval
  @Deprecated("Use Kotlin UI DSL Version 2", level = DeprecationLevel.HIDDEN)
  fun checkBox(@Checkbox text: String, prop: KMutableProperty0<Boolean>, @DetailedDescription comment: String? = null): CellBuilder<JBCheckBox> {
    return checkBox(text, prop.intToBinding(), comment)
  }

  @ApiStatus.ScheduledForRemoval
  @Deprecated("Use Kotlin UI DSL Version 2", level = DeprecationLevel.HIDDEN)
  fun checkBox(@Checkbox text: String, getter: () -> Boolean, setter: (Boolean) -> Unit, @DetailedDescription comment: String? = null): CellBuilder<JBCheckBox> {
    return checkBox(text, PropertyBinding(getter, setter), comment)
  }

  @ApiStatus.ScheduledForRemoval
  @Deprecated("Use Kotlin UI DSL Version 2")
  private fun checkBox(@Checkbox text: String,
                       modelBinding: PropertyBinding<Boolean>,
                       @DetailedDescription comment: String?): CellBuilder<JBCheckBox> {
    val component = JBCheckBox(text, modelBinding.get())
    return component(comment = comment).withBindingInt(AbstractButton::isSelected, AbstractButton::setSelected, modelBinding)
  }

  @ApiStatus.ScheduledForRemoval
  @Deprecated("Use Kotlin UI DSL Version 2", level = DeprecationLevel.HIDDEN)
  fun checkBox(text: @Checkbox String,
               property: GraphProperty<Boolean>,
               comment: @DetailedDescription String? = null): CellBuilder<JBCheckBox> {
    val component = JBCheckBox(text, property.get())
    return component(comment = comment).withGraphProperty(property).intApplyToComponent { component.bind(property) }
  }

  @ApiStatus.ScheduledForRemoval
  @Deprecated("Use Kotlin UI DSL Version 2", level = DeprecationLevel.HIDDEN)
  fun <T> comboBox(model: ComboBoxModel<T>,
                   getter: () -> T?,
                   setter: (T?) -> Unit,
                   renderer: ListCellRenderer<T?>? = null): CellBuilder<ComboBox<T>> {
    return comboBoxInt(model, PropertyBinding(getter, setter), renderer)
  }

  @ApiStatus.ScheduledForRemoval
  @Deprecated("Use Kotlin UI DSL Version 2", level = DeprecationLevel.HIDDEN)
  fun <T> comboBox(model: ComboBoxModel<T>,
                   modelBinding: PropertyBinding<T?>,
                   renderer: ListCellRenderer<T?>? = null): CellBuilder<ComboBox<T>> {
    return comboBoxInt(model, modelBinding, renderer)
  }

  @ApiStatus.ScheduledForRemoval
  @ApiStatus.Internal
  @Deprecated("Use Kotlin UI DSL Version 2")
  fun <T> comboBoxInt(model: ComboBoxModel<T>,
                   modelBinding: PropertyBinding<T?>,
                   renderer: ListCellRenderer<T?>? = null): CellBuilder<ComboBox<T>> {
    return component(ComboBox(model))
      .intApplyToComponent {
        this.renderer = renderer ?: SimpleListCellRenderer.create("") { it.toString() }
        selectedItem = modelBinding.get()
      }
      .withBindingInt(
        { component -> component.selectedItem as T? },
        { component, value -> component.setSelectedItem(value) },
        modelBinding
      )
  }

  @ApiStatus.ScheduledForRemoval
  @Deprecated("Use Kotlin UI DSL Version 2", level = DeprecationLevel.HIDDEN)
  fun <T> comboBox(
    model: ComboBoxModel<T>,
    property: GraphProperty<T>,
    renderer: ListCellRenderer<T?>? = null
  ): CellBuilder<ComboBox<T>> {
    return comboBoxInt(model, PropertyBinding(property::get, property::set).intToNullable(), renderer)
      .withGraphProperty(property)
      .intApplyToComponent { bind(property) }
  }

  @ApiStatus.ScheduledForRemoval
  @Deprecated("Use Kotlin UI DSL Version 2", level = DeprecationLevel.HIDDEN)
  fun textField(prop: KMutableProperty0<String>, columns: Int? = null): CellBuilder<JBTextField> = textFieldInt(prop.intToBinding(), columns)

  @ApiStatus.ScheduledForRemoval
  @Deprecated("Use Kotlin UI DSL Version 2", level = DeprecationLevel.HIDDEN)
  fun textField(getter: () -> String, setter: (String) -> Unit, columns: Int? = null): CellBuilder<JBTextField> = textFieldInt(PropertyBinding(getter, setter), columns)

  @ApiStatus.ScheduledForRemoval
  @Deprecated("Use Kotlin UI DSL Version 2", level = DeprecationLevel.HIDDEN)
  fun textField(binding: PropertyBinding<@Nls String>, columns: Int? = null): CellBuilder<JBTextField> {
    return textFieldInt(binding, columns)
  }

  private fun textFieldInt(binding: PropertyBinding<@Nls String>, columns: Int? = null): CellBuilder<JBTextField> {
    return component(JBTextField(binding.get(), columns ?: 0))
      .withBindingInt(JTextComponent::getText, JTextComponent::setText, binding)
  }

  @ApiStatus.ScheduledForRemoval
  @Deprecated("Use Kotlin UI DSL Version 2", level = DeprecationLevel.HIDDEN)
  fun spinner(getter: () -> Int, setter: (Int) -> Unit, minValue: Int, maxValue: Int, step: Int = 1): CellBuilder<JBIntSpinner> {
    val spinner = JBIntSpinner(getter(), minValue, maxValue, step)
    return component(spinner).withBindingInt(JBIntSpinner::getNumber, JBIntSpinner::setNumber, PropertyBinding(getter, setter))
  }

  @ApiStatus.ScheduledForRemoval
  @Deprecated("Use Kotlin UI DSL Version 2", level = DeprecationLevel.HIDDEN)
  fun scrollPane(component: Component): CellBuilder<JScrollPane> {
    return component(JBScrollPane(component))
  }

  @ApiStatus.ScheduledForRemoval
  @Deprecated("Use Kotlin UI DSL Version 2")
  abstract fun <T : JComponent> component(component: T): CellBuilder<T>

  @ApiStatus.ScheduledForRemoval
  @Deprecated("Use Kotlin UI DSL Version 2")
  abstract fun <T : JComponent> component(component: T, viewComponent: JComponent): CellBuilder<T>

  @ApiStatus.ScheduledForRemoval
  @Deprecated("Use Kotlin UI DSL Version 2")
  operator fun <T : JComponent> T.invoke(
    vararg constraints: CCFlags,
    growPolicy: GrowPolicy? = null,
    @DetailedDescription comment: String? = null
  ): CellBuilder<T> = component(this).apply {
    constraints(*constraints)
    if (comment != null) comment(comment)
    if (growPolicy != null) growPolicy(growPolicy)
  }

  @ApiStatus.ScheduledForRemoval
  @Deprecated("Use Kotlin UI DSL Version 2")
  internal fun internalPlaceholder(): CellBuilder<JComponent>{
    return component(JPanel().apply {
      minimumSize = Dimension(0, 0)
      preferredSize = Dimension(0, 0)
      maximumSize = Dimension(0, 0)
    })
  }
}

@ApiStatus.ScheduledForRemoval
@Deprecated("Use Kotlin UI DSL Version 2")
class InnerCell(val cell: Cell) : Cell() {
  @ApiStatus.ScheduledForRemoval
  @Deprecated("Use Kotlin UI DSL Version 2")
  override fun <T : JComponent> component(component: T): CellBuilder<T> {
    return cell.component(component)
  }

  @ApiStatus.ScheduledForRemoval
  @Deprecated("Use Kotlin UI DSL Version 2")
  override fun <T : JComponent> component(component: T, viewComponent: JComponent): CellBuilder<T> {
    return cell.component(component, viewComponent)
  }
}

@ApiStatus.ScheduledForRemoval
@Deprecated("Use com.intellij.ui.dsl.listCellRenderer.BuilderKt.textListCellRenderer/listCellRenderer instead", level = DeprecationLevel.HIDDEN)
fun <T> listCellRenderer(renderer: SimpleListCellRenderer<T?>.(value: T, index: Int, isSelected: Boolean) -> Unit): SimpleListCellRenderer<T?> {
  return object : SimpleListCellRenderer<T?>() {
    override fun customize(list: JList<out T?>, value: T?, index: Int, selected: Boolean, hasFocus: Boolean) {
      if (value != null) {
        renderer(this, value, index, selected)
      }
    }
  }
}
