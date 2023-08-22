// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.layout

import com.intellij.BundleBase
import com.intellij.icons.AllIcons
import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.observable.properties.GraphProperty
import com.intellij.openapi.observable.util.bind
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.ui.emptyText
import com.intellij.openapi.ui.panel.ComponentPanelBuilder
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.NlsContexts.*
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.*
import com.intellij.ui.components.*
import com.intellij.ui.components.fields.ExpandableTextField
import com.intellij.util.Function
import com.intellij.util.execution.ParametersListUtil
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.UIUtil
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import java.awt.Component
import java.awt.Dimension
import java.awt.event.ActionEvent
import java.awt.event.ActionListener
import java.awt.event.MouseEvent
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
@Deprecated("Use MutableProperty and Kotlin UI DSL 2")
fun <T> PropertyBinding<T>.toNullable(): PropertyBinding<T?> {
  return PropertyBinding({ get() }, { set(it!!) })
}

@ApiStatus.ScheduledForRemoval
@Deprecated("Use Kotlin UI DSL Version 2")
inline fun <reified T : Any> KMutableProperty0<T>.toBinding(): PropertyBinding<T> {
  return createPropertyBinding(this, T::class.javaPrimitiveType ?: T::class.java)
}

@ApiStatus.ScheduledForRemoval
@Deprecated("Use Kotlin UI DSL Version 2")
inline fun <reified T : Any> KMutableProperty0<T?>.toNullableBinding(defaultValue: T): PropertyBinding<T> {
  return PropertyBinding({ get() ?: defaultValue }, { set(it) })
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
  @Deprecated("Use Kotlin UI DSL Version 2")
  fun focused(): CellBuilder<T>

  @ApiStatus.ScheduledForRemoval
  @Deprecated("Use Kotlin UI DSL Version 2")
  fun withValidationOnApply(callback: ValidationInfoBuilder.(T) -> ValidationInfo?): CellBuilder<T>

  @ApiStatus.ScheduledForRemoval
  @Deprecated("Use Kotlin UI DSL Version 2")
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

  /**
   * All components of the same group share will get the same BoundSize (min/preferred/max),
   * which is that of the biggest component in the group
   */
  @ApiStatus.ScheduledForRemoval
  @Deprecated("Use Kotlin UI DSL Version 2, see Cell.widthGroup()")
  fun sizeGroup(name: String): CellBuilder<T>

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
  @Deprecated("Use Kotlin UI DSL Version 2")
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
  @Deprecated("Use Kotlin UI DSL Version 2")
  fun withGraphProperty(property: GraphProperty<*>): CellBuilder<T>

  @ApiStatus.ScheduledForRemoval
  @Deprecated("Use Kotlin UI DSL Version 2")
  fun enabled(isEnabled: Boolean)

  @ApiStatus.ScheduledForRemoval
  @Deprecated("Use Kotlin UI DSL Version 2")
  fun enableIf(predicate: ComponentPredicate): CellBuilder<T>

  @ApiStatus.ScheduledForRemoval
  @Deprecated("Use Kotlin UI DSL Version 2", level = DeprecationLevel.HIDDEN)
  fun visible(isVisible: Boolean)

  @ApiStatus.ScheduledForRemoval
  @Deprecated("Use Kotlin UI DSL Version 2")
  fun visibleIf(predicate: ComponentPredicate): CellBuilder<T>

  @ApiStatus.ScheduledForRemoval
  @Deprecated("Use Kotlin UI DSL Version 2", level = DeprecationLevel.HIDDEN)
  fun withErrorOnApplyIf(@DialogMessage message: String, callback: (T) -> Boolean): CellBuilder<T> {
    withValidationOnApply { if (callback(it)) error(message) else null }
    return this
  }

  @Deprecated("Use Kotlin UI DSL Version 2")
  @ApiStatus.Internal
  fun shouldSaveOnApply(): Boolean

  @ApiStatus.ScheduledForRemoval
  @Deprecated("Use Kotlin UI DSL Version 2", level = DeprecationLevel.HIDDEN)
  fun withLargeLeftGap(): CellBuilder<T>

  @ApiStatus.ScheduledForRemoval
  @Deprecated("Use Kotlin UI DSL Version 2", level = DeprecationLevel.HIDDEN)
  fun withLeftGap(): CellBuilder<T>
}

@ApiStatus.ScheduledForRemoval
@Deprecated("Use Kotlin UI DSL Version 2")
fun <T : JComponent> CellBuilder<T>.applyToComponent(task: T.() -> Unit): CellBuilder<T> {
  return also { task(component) }
}

@ApiStatus.ScheduledForRemoval
@Deprecated("Use Kotlin UI DSL Version 2")
fun <T : JTextComponent> CellBuilder<T>.withTextBinding(modelBinding: PropertyBinding<String>): CellBuilder<T> {
  return withBinding(JTextComponent::getText, JTextComponent::setText, modelBinding)
}

@ApiStatus.ScheduledForRemoval
@Deprecated("Use Kotlin UI DSL Version 2")
fun <T : AbstractButton> CellBuilder<T>.withSelectedBinding(modelBinding: PropertyBinding<Boolean>): CellBuilder<T> {
  return withBinding(AbstractButton::isSelected, AbstractButton::setSelected, modelBinding)
}

@get:Deprecated("Use Kotlin UI DSL Version 2")
@get:ApiStatus.ScheduledForRemoval
@Deprecated("Use Kotlin UI DSL Version 2")
val CellBuilder<AbstractButton>.selected: ComponentPredicate
  get() = component.selected

@ApiStatus.ScheduledForRemoval
@Deprecated("Use Kotlin UI DSL Version 2")
internal const val UNBOUND_RADIO_BUTTON: String = "unbound.radio.button"

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
  @Deprecated("Use Kotlin UI DSL Version 2")
  val growX: CCFlags = CCFlags.growX

  @ApiStatus.ScheduledForRemoval
  @Deprecated("Use Kotlin UI DSL Version 2")
  val growY: CCFlags = CCFlags.growY

  @ApiStatus.ScheduledForRemoval
  @Deprecated("Use Kotlin UI DSL Version 2")
  val grow: CCFlags = CCFlags.grow

  /**
   * Makes the column that the component is residing in grow with `weight`.
   */
  @ApiStatus.ScheduledForRemoval
  @Deprecated("Use Kotlin UI DSL Version 2")
  val pushX: CCFlags = CCFlags.pushX

  /**
   * Makes the row that the component is residing in grow with `weight`.
   */
  @ApiStatus.ScheduledForRemoval
  @Deprecated("Use Kotlin UI DSL Version 2", level = DeprecationLevel.HIDDEN)
  val pushY: CCFlags = CCFlags.pushY

  @ApiStatus.ScheduledForRemoval
  @Deprecated("Use Kotlin UI DSL Version 2", level = DeprecationLevel.HIDDEN)
  val push: CCFlags = CCFlags.push

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
  @Deprecated("Use Kotlin UI DSL Version 2")
  @ApiStatus.Internal
  fun label(@Label text: String,
            font: JBFont,
            fontColor: UIUtil.FontColor? = null): CellBuilder<JLabel> {
    val label = Label(text, fontColor = fontColor, font = font)
    return component(label)
  }

  @ApiStatus.ScheduledForRemoval
  @Deprecated("Use Kotlin UI DSL Version 2")
  fun link(@LinkLabel text: String,
           style: UIUtil.ComponentStyle? = null,
           action: () -> Unit): CellBuilder<JComponent> {
    val result = Link(text, style, action)
    return component(result)
  }

  @ApiStatus.ScheduledForRemoval
  @Deprecated("Use Kotlin UI DSL Version 2")
  fun browserLink(@LinkLabel text: String, url: String): CellBuilder<JComponent> {
    val result = BrowserLink(text, url)
    return component(result)
  }

  @ApiStatus.ScheduledForRemoval
  @Deprecated("Use Kotlin UI DSL Version 2")
  fun button(@Button text: String, actionListener: (event: ActionEvent) -> Unit): CellBuilder<JButton> {
    val button = JButton(BundleBase.replaceMnemonicAmpersand(text))
    button.addActionListener(actionListener)
    return component(button)
  }

  @ApiStatus.ScheduledForRemoval
  @Deprecated("Use Kotlin UI DSL Version 2")
  inline fun checkBox(@Checkbox text: String,
                      isSelected: Boolean = false,
                      @DetailedDescription comment: String? = null,
                      crossinline actionListener: (event: ActionEvent, component: JCheckBox) -> Unit): CellBuilder<JBCheckBox> {
    return checkBox(text, isSelected, comment)
      .applyToComponent {
        addActionListener(ActionListener { actionListener(it, this) })
      }
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
  @Deprecated("Use Kotlin UI DSL Version 2")
  fun checkBox(@Checkbox text: String, prop: KMutableProperty0<Boolean>, @DetailedDescription comment: String? = null): CellBuilder<JBCheckBox> {
    return checkBox(text, prop.toBinding(), comment)
  }

  @ApiStatus.ScheduledForRemoval
  @Deprecated("Use Kotlin UI DSL Version 2")
  fun checkBox(@Checkbox text: String, getter: () -> Boolean, setter: (Boolean) -> Unit, @DetailedDescription comment: String? = null): CellBuilder<JBCheckBox> {
    return checkBox(text, PropertyBinding(getter, setter), comment)
  }

  @ApiStatus.ScheduledForRemoval
  @Deprecated("Use Kotlin UI DSL Version 2")
  private fun checkBox(@Checkbox text: String,
                       modelBinding: PropertyBinding<Boolean>,
                       @DetailedDescription comment: String?): CellBuilder<JBCheckBox> {
    val component = JBCheckBox(text, modelBinding.get())
    return component(comment = comment).withSelectedBinding(modelBinding)
  }

  @ApiStatus.ScheduledForRemoval
  @Deprecated("Use Kotlin UI DSL Version 2")
  fun checkBox(@Checkbox text: String,
               property: GraphProperty<Boolean>,
               @DetailedDescription comment: String? = null): CellBuilder<JBCheckBox> {
    val component = JBCheckBox(text, property.get())
    return component(comment = comment).withGraphProperty(property).applyToComponent { component.bind(property) }
  }

  @ApiStatus.ScheduledForRemoval
  @Deprecated("Use Kotlin UI DSL Version 2")
  @ApiStatus.Internal
  open fun radioButton(@RadioButton text: String, @Nls comment: String? = null): CellBuilder<JBRadioButton> {
    val component = JBRadioButton(text)
    component.putClientProperty(UNBOUND_RADIO_BUTTON, true)
    return component(comment = comment)
  }

  @ApiStatus.ScheduledForRemoval
  @Deprecated("Use Kotlin UI DSL Version 2")
  open fun radioButton(@RadioButton text: String, getter: () -> Boolean, setter: (Boolean) -> Unit, @Nls comment: String? = null): CellBuilder<JBRadioButton> {
    val component = JBRadioButton(text, getter())
    return component(comment = comment).withSelectedBinding(PropertyBinding(getter, setter))
  }

  @ApiStatus.ScheduledForRemoval
  @Deprecated("Use Kotlin UI DSL Version 2")
  open fun radioButton(@RadioButton text: String, prop: KMutableProperty0<Boolean>, @Nls comment: String? = null): CellBuilder<JBRadioButton> {
    val component = JBRadioButton(text, prop.get())
    return component(comment = comment).withSelectedBinding(prop.toBinding())
  }

  @ApiStatus.ScheduledForRemoval
  @Deprecated("Use Kotlin UI DSL Version 2")
  fun <T> comboBox(model: ComboBoxModel<T>,
                   getter: () -> T?,
                   setter: (T?) -> Unit,
                   renderer: ListCellRenderer<T?>? = null): CellBuilder<ComboBox<T>> {
    return comboBox(model, PropertyBinding(getter, setter), renderer)
  }

  @ApiStatus.ScheduledForRemoval
  @Deprecated("Use Kotlin UI DSL Version 2")
  fun <T> comboBox(model: ComboBoxModel<T>,
                   modelBinding: PropertyBinding<T?>,
                   renderer: ListCellRenderer<T?>? = null): CellBuilder<ComboBox<T>> {
    return component(ComboBox(model))
      .applyToComponent {
        this.renderer = renderer ?: SimpleListCellRenderer.create("") { it.toString() }
        selectedItem = modelBinding.get()
      }
      .withBinding(
        { component -> component.selectedItem as T? },
        { component, value -> component.setSelectedItem(value) },
        modelBinding
      )
  }

  @ApiStatus.ScheduledForRemoval
  @Deprecated("Use Kotlin UI DSL Version 2")
  inline fun <reified T : Any> comboBox(
    model: ComboBoxModel<T>,
    prop: KMutableProperty0<T>,
    renderer: ListCellRenderer<T?>? = null
  ): CellBuilder<ComboBox<T>> {
    return comboBox(model, prop.toBinding().toNullable(), renderer)
  }

  @ApiStatus.ScheduledForRemoval
  @Deprecated("Use Kotlin UI DSL Version 2")
  fun <T> comboBox(
    model: ComboBoxModel<T>,
    property: GraphProperty<T>,
    renderer: ListCellRenderer<T?>? = null
  ): CellBuilder<ComboBox<T>> {
    return comboBox(model, PropertyBinding(property::get, property::set).toNullable(), renderer)
      .withGraphProperty(property)
      .applyToComponent { bind(property) }
  }

  @ApiStatus.ScheduledForRemoval
  @Deprecated("Use Kotlin UI DSL Version 2")
  fun textField(prop: KMutableProperty0<String>, columns: Int? = null): CellBuilder<JBTextField> = textField(prop.toBinding(), columns)

  @ApiStatus.ScheduledForRemoval
  @Deprecated("Use Kotlin UI DSL Version 2")
  fun textField(getter: () -> String, setter: (String) -> Unit, columns: Int? = null): CellBuilder<JBTextField> = textField(PropertyBinding(getter, setter), columns)

  @ApiStatus.ScheduledForRemoval
  @Deprecated("Use Kotlin UI DSL Version 2")
  fun textField(binding: PropertyBinding<String>, columns: Int? = null): CellBuilder<JBTextField> {
    return component(JBTextField(binding.get(), columns ?: 0))
      .withTextBinding(binding)
  }

  @ApiStatus.ScheduledForRemoval
  @Deprecated("Use Kotlin UI DSL Version 2")
  @ApiStatus.Internal
  fun textField(property: GraphProperty<String>, columns: Int? = null): CellBuilder<JBTextField> {
    return textField(property::get, property::set, columns)
      .withGraphProperty(property)
      .applyToComponent { bind(property) }
  }

  @ApiStatus.ScheduledForRemoval
  @Deprecated("Use Kotlin UI DSL Version 2")
  @ApiStatus.Internal
  fun scrollableTextArea(getter: () -> String, setter: (String) -> Unit, rows: Int? = null): CellBuilder<JBTextArea> = scrollableTextArea(PropertyBinding(getter, setter), rows)

  @Deprecated("Use Kotlin UI DSL Version 2")
  private fun scrollableTextArea(binding: PropertyBinding<String>, rows: Int? = null): CellBuilder<JBTextArea> {
    val textArea = JBTextArea(binding.get(), rows ?: 0, 0)
    val scrollPane = JBScrollPane(textArea)
    return component(textArea, scrollPane)
      .withTextBinding(binding)
  }

  @JvmOverloads
  @ApiStatus.ScheduledForRemoval
  @Deprecated("Use Kotlin UI DSL Version 2", level = DeprecationLevel.HIDDEN)
  fun intTextField(prop: KMutableProperty0<Int>, columns: Int? = null, range: IntRange? = null): CellBuilder<JBTextField> {
    val binding = prop.toBinding()
    return textField(
      { binding.get().toString() },
      { value -> value.toIntOrNull()?.let { intValue -> binding.set(range?.let { intValue.coerceIn(it.first, it.last) } ?: intValue) } },
      columns
    ).withValidationOnInput {
      val value = it.text.toIntOrNull()
      when {
        value == null -> error(UIBundle.message("please.enter.a.number"))
        range != null && value !in range -> error(UIBundle.message("please.enter.a.number.from.0.to.1", range.first, range.last))
        else -> null
      }
    }
  }

  @ApiStatus.ScheduledForRemoval
  @Deprecated("Use Kotlin UI DSL Version 2")
  fun spinner(prop: KMutableProperty0<Int>, minValue: Int, maxValue: Int, step: Int = 1): CellBuilder<JBIntSpinner> {
    val spinner = JBIntSpinner(prop.get(), minValue, maxValue, step)
    return component(spinner).withBinding(JBIntSpinner::getNumber, JBIntSpinner::setNumber, prop.toBinding())
  }

  @ApiStatus.ScheduledForRemoval
  @Deprecated("Use Kotlin UI DSL Version 2", level = DeprecationLevel.HIDDEN)
  fun spinner(getter: () -> Int, setter: (Int) -> Unit, minValue: Int, maxValue: Int, step: Int = 1): CellBuilder<JBIntSpinner> {
    val spinner = JBIntSpinner(getter(), minValue, maxValue, step)
    return component(spinner).withBinding(JBIntSpinner::getNumber, JBIntSpinner::setNumber, PropertyBinding(getter, setter))
  }

  @ApiStatus.ScheduledForRemoval
  @Deprecated("Use Kotlin UI DSL Version 2", level = DeprecationLevel.HIDDEN)
  @ApiStatus.Internal
  fun textFieldWithHistoryWithBrowseButton(
    getter: () -> String,
    setter: (String) -> Unit,
    @DialogTitle browseDialogTitle: String,
    project: Project? = null,
    fileChooserDescriptor: FileChooserDescriptor = FileChooserDescriptorFactory.createSingleFileNoJarsDescriptor(),
    historyProvider: (() -> List<String>)? = null,
    fileChosen: ((chosenFile: VirtualFile) -> String)? = null
  ): CellBuilder<TextFieldWithHistoryWithBrowseButton> {
    val textField = textFieldWithHistoryWithBrowseButton(project, browseDialogTitle, fileChooserDescriptor, historyProvider, fileChosen)
    val modelBinding = PropertyBinding(getter, setter)
    textField.text = modelBinding.get()
    return component(textField)
      .withBinding(TextFieldWithHistoryWithBrowseButton::getText, TextFieldWithHistoryWithBrowseButton::setText, modelBinding)
  }

  @ApiStatus.ScheduledForRemoval
  @Deprecated("Use Kotlin UI DSL Version 2")
  fun textFieldWithBrowseButton(
    @DialogTitle browseDialogTitle: String? = null,
    value: String? = null,
    project: Project? = null,
    fileChooserDescriptor: FileChooserDescriptor = FileChooserDescriptorFactory.createSingleFileNoJarsDescriptor(),
    fileChosen: ((chosenFile: VirtualFile) -> String)? = null
  ): CellBuilder<TextFieldWithBrowseButton> {
    val textField = textFieldWithBrowseButton(project, browseDialogTitle, fileChooserDescriptor, fileChosen)
    if (value != null) textField.text = value
    return component(textField)
  }

  @ApiStatus.ScheduledForRemoval
  @Deprecated("Use Kotlin UI DSL Version 2")
  fun textFieldWithBrowseButton(
    prop: KMutableProperty0<String>,
    @DialogTitle browseDialogTitle: String? = null,
    project: Project? = null,
    fileChooserDescriptor: FileChooserDescriptor = FileChooserDescriptorFactory.createSingleFileNoJarsDescriptor(),
    fileChosen: ((chosenFile: VirtualFile) -> String)? = null
  ): CellBuilder<TextFieldWithBrowseButton> {
    val modelBinding = prop.toBinding()
    return textFieldWithBrowseButton(modelBinding, browseDialogTitle, project, fileChooserDescriptor, fileChosen)
  }

  @ApiStatus.ScheduledForRemoval
  @Deprecated("Use Kotlin UI DSL Version 2")
  @ApiStatus.Internal
  fun textFieldWithBrowseButton(
    getter: () -> String,
    setter: (String) -> Unit,
    @DialogTitle browseDialogTitle: String? = null,
    project: Project? = null,
    fileChooserDescriptor: FileChooserDescriptor = FileChooserDescriptorFactory.createSingleFileNoJarsDescriptor(),
    fileChosen: ((chosenFile: VirtualFile) -> String)? = null
  ): CellBuilder<TextFieldWithBrowseButton> {
    val modelBinding = PropertyBinding(getter, setter)
    return textFieldWithBrowseButton(modelBinding, browseDialogTitle, project, fileChooserDescriptor, fileChosen)
  }

  @ApiStatus.ScheduledForRemoval
  @Deprecated("Use Kotlin UI DSL Version 2")
  @ApiStatus.Internal
  fun textFieldWithBrowseButton(
    modelBinding: PropertyBinding<String>,
    @DialogTitle browseDialogTitle: String? = null,
    project: Project? = null,
    fileChooserDescriptor: FileChooserDescriptor = FileChooserDescriptorFactory.createSingleFileNoJarsDescriptor(),
    fileChosen: ((chosenFile: VirtualFile) -> String)? = null
  ): CellBuilder<TextFieldWithBrowseButton> {
    val textField = textFieldWithBrowseButton(project, browseDialogTitle, fileChooserDescriptor, fileChosen)
    textField.text = modelBinding.get()
    return component(textField)
      .constraints(growX)
      .withBinding(TextFieldWithBrowseButton::getText, TextFieldWithBrowseButton::setText, modelBinding)
  }

  @ApiStatus.ScheduledForRemoval
  @Deprecated("Use Kotlin UI DSL Version 2")
  fun textFieldWithBrowseButton(
    property: GraphProperty<String>,
    emptyTextProperty: GraphProperty<String>,
    @DialogTitle browseDialogTitle: String? = null,
    project: Project? = null,
    fileChooserDescriptor: FileChooserDescriptor = FileChooserDescriptorFactory.createSingleFileNoJarsDescriptor(),
    fileChosen: ((chosenFile: VirtualFile) -> String)? = null
  ): CellBuilder<TextFieldWithBrowseButton> {
    return textFieldWithBrowseButton(property, browseDialogTitle, project, fileChooserDescriptor, fileChosen)
      .applyToComponent { emptyText.bind(emptyTextProperty) }
      .applyToComponent { emptyText.text = emptyTextProperty.get() }
  }

  @ApiStatus.ScheduledForRemoval
  @Deprecated("Use Kotlin UI DSL Version 2")
  @ApiStatus.Internal
  fun textFieldWithBrowseButton(
    property: GraphProperty<String>,
    @DialogTitle browseDialogTitle: String? = null,
    project: Project? = null,
    fileChooserDescriptor: FileChooserDescriptor = FileChooserDescriptorFactory.createSingleFileNoJarsDescriptor(),
    fileChosen: ((chosenFile: VirtualFile) -> String)? = null
  ): CellBuilder<TextFieldWithBrowseButton> {
    return textFieldWithBrowseButton(property::get, property::set, browseDialogTitle, project, fileChooserDescriptor, fileChosen)
      .withGraphProperty(property)
      .applyToComponent { bind(property) }
  }

  @ApiStatus.ScheduledForRemoval
  @Deprecated("Use Kotlin UI DSL Version 2", level = DeprecationLevel.HIDDEN)
  fun gearButton(vararg actions: AnAction): CellBuilder<JComponent> {
    val label = JLabel(LayeredIcon.GEAR_WITH_DROPDOWN)
    label.disabledIcon = AllIcons.General.GearPlain
    object : ClickListener() {
      override fun onClick(e: MouseEvent, clickCount: Int): Boolean {
        if (!label.isEnabled) return true
        JBPopupFactory.getInstance()
          .createActionGroupPopup(null, DefaultActionGroup(*actions), DataManager.getInstance().getDataContext(label), true, null, 10)
          .showUnderneathOf(label)
        return true
      }
    }.installOn(label)

    return component(label)
  }

  @ApiStatus.ScheduledForRemoval
  @Deprecated("Use Kotlin UI DSL Version 2")
  fun expandableTextField(getter: () -> String,
                          setter: (String) -> Unit,
                          parser: Function<in String, out MutableList<String>> = ParametersListUtil.DEFAULT_LINE_PARSER,
                          joiner: Function<in MutableList<String>, String> = ParametersListUtil.DEFAULT_LINE_JOINER)
    : CellBuilder<ExpandableTextField> {
    return ExpandableTextField(parser, joiner)()
      .withBinding({ editor -> editor.text.orEmpty() },
                   { editor, value -> editor.text = value },
                   PropertyBinding(getter, setter))
  }

  @ApiStatus.ScheduledForRemoval
  @Deprecated("Use Kotlin UI DSL Version 2")
  fun expandableTextField(prop: KMutableProperty0<String>,
                          parser: Function<in String, out MutableList<String>> = ParametersListUtil.DEFAULT_LINE_PARSER,
                          joiner: Function<in MutableList<String>, String> = ParametersListUtil.DEFAULT_LINE_JOINER)
    : CellBuilder<ExpandableTextField> {
    return expandableTextField(prop::get, prop::set, parser, joiner)
  }

  @ApiStatus.ScheduledForRemoval
  @Deprecated("Use Kotlin UI DSL Version 2")
  fun expandableTextField(prop: GraphProperty<String>,
                          parser: Function<in String, out MutableList<String>> = ParametersListUtil.DEFAULT_LINE_PARSER,
                          joiner: Function<in MutableList<String>, String> = ParametersListUtil.DEFAULT_LINE_JOINER)
    : CellBuilder<ExpandableTextField> {
    return expandableTextField(prop::get, prop::set, parser, joiner)
      .withGraphProperty(prop)
      .applyToComponent { bind(prop) }
  }

  @ApiStatus.ScheduledForRemoval
  @Deprecated("Use Kotlin UI DSL Version 2")
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
@Deprecated("Use simplified method com.intellij.ui.dsl.builder.UtilsKt.listCellRenderer instead")
fun <T> listCellRenderer(renderer: SimpleListCellRenderer<T?>.(value: T, index: Int, isSelected: Boolean) -> Unit): SimpleListCellRenderer<T?> {
  return object : SimpleListCellRenderer<T?>() {
    override fun customize(list: JList<out T?>, value: T?, index: Int, selected: Boolean, hasFocus: Boolean) {
      if (value != null) {
        renderer(this, value, index, selected)
      }
    }
  }
}
