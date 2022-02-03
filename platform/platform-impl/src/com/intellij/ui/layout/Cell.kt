// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.layout

import com.intellij.BundleBase
import com.intellij.icons.AllIcons
import com.intellij.ide.ui.UINumericRange
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.observable.properties.*
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.*
import com.intellij.openapi.ui.panel.ComponentPanelBuilder
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.NlsContexts.*
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.*
import com.intellij.ui.components.*
import com.intellij.ui.components.fields.ExpandableTextField
import com.intellij.util.Function
import com.intellij.util.MathUtil
import com.intellij.util.execution.ParametersListUtil
import com.intellij.openapi.observable.util.bind
import com.intellij.util.ui.*
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NonNls
import java.awt.Component
import java.awt.Dimension
import java.awt.event.*
import java.util.*
import javax.swing.*
import javax.swing.text.JTextComponent
import kotlin.jvm.internal.CallableReference
import kotlin.reflect.KMutableProperty0

@DslMarker
annotation class CellMarker

data class PropertyBinding<V>(val get: () -> V, val set: (V) -> Unit)

@PublishedApi
@ApiStatus.ScheduledForRemoval(inVersion = "2022.2")
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

fun <T> PropertyBinding<T>.toNullable(): PropertyBinding<T?> {
  return PropertyBinding<T?>({ get() }, { set(it!!) })
}

inline fun <reified T : Any> KMutableProperty0<T>.toBinding(): PropertyBinding<T> {
  return createPropertyBinding(this, T::class.javaPrimitiveType ?: T::class.java)
}

inline fun <reified T : Any> KMutableProperty0<T?>.toNullableBinding(defaultValue: T): PropertyBinding<T> {
  return PropertyBinding({ get() ?: defaultValue }, { set(it) })
}

class ValidationInfoBuilder(val component: JComponent) {
  fun error(@DialogMessage message: String): ValidationInfo = ValidationInfo(message, component)
  fun warning(@DialogMessage message: String): ValidationInfo = ValidationInfo(message, component).asWarning().withOKEnabled()
}

interface CellBuilder<out T : JComponent> {
  val component: T

  fun comment(@DetailedDescription text: String, maxLineLength: Int = ComponentPanelBuilder.MAX_COMMENT_WIDTH,
              forComponent: Boolean = false): CellBuilder<T>

  @ApiStatus.ScheduledForRemoval(inVersion = "2022.2")
  @Deprecated("Use Kotlin UI DSL Version 2")
  fun commentComponent(component: JComponent, forComponent: Boolean = false): CellBuilder<T>

  fun focused(): CellBuilder<T>
  fun withValidationOnApply(callback: ValidationInfoBuilder.(T) -> ValidationInfo?): CellBuilder<T>
  fun withValidationOnInput(callback: ValidationInfoBuilder.(T) -> ValidationInfo?): CellBuilder<T>
  fun onApply(callback: () -> Unit): CellBuilder<T>
  fun onReset(callback: () -> Unit): CellBuilder<T>
  fun onIsModified(callback: () -> Boolean): CellBuilder<T>

  /**
   * All components of the same group share will get the same BoundSize (min/preferred/max),
   * which is that of the biggest component in the group
   */
  @ApiStatus.ScheduledForRemoval(inVersion = "2022.3")
  @Deprecated("Use Kotlin UI DSL Version 2, see Cell.widthGroup()")
  fun sizeGroup(name: String): CellBuilder<T>
  fun growPolicy(growPolicy: GrowPolicy): CellBuilder<T>
  fun constraints(vararg constraints: CCFlags): CellBuilder<T>

  /**
   * If this method is called, the value of the component will be stored to the backing property only if the component is enabled.
   */
  fun applyIfEnabled(): CellBuilder<T>

  @ApiStatus.Experimental
  fun accessibleName(@Nls name: String): CellBuilder<T> {
    component.accessibleContext.accessibleName = name

    return this
  }

  @ApiStatus.Experimental
  fun accessibleDescription(@Nls description: String): CellBuilder<T> {
    component.accessibleContext.accessibleDescription = description

    return this
  }

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

  fun withGraphProperty(property: GraphProperty<*>): CellBuilder<T>

  fun enabled(isEnabled: Boolean)
  fun enableIf(predicate: ComponentPredicate): CellBuilder<T>

  fun visible(isVisible: Boolean)
  fun visibleIf(predicate: ComponentPredicate): CellBuilder<T>

  fun withErrorOnApplyIf(@DialogMessage message: String, callback: (T) -> Boolean): CellBuilder<T> {
    withValidationOnApply { if (callback(it)) error(message) else null }
    return this
  }

  @ApiStatus.Internal
  fun shouldSaveOnApply(): Boolean

  fun withLargeLeftGap(): CellBuilder<T>

  fun withLeftGap(): CellBuilder<T>

  @Deprecated("Prefer not to use hardcoded values")
  @ApiStatus.ScheduledForRemoval(inVersion = "2021.3")
  fun withLeftGap(gapLeft: Int): CellBuilder<T>
}

internal interface CheckboxCellBuilder {
  fun actsAsLabel()
}

fun <T : JCheckBox> CellBuilder<T>.actsAsLabel(): CellBuilder<T> {
  (this as CheckboxCellBuilder).actsAsLabel()
  return this
}

fun <T : JComponent> CellBuilder<T>.applyToComponent(task: T.() -> Unit): CellBuilder<T> {
  return also { task(component) }
}

internal interface ScrollPaneCellBuilder {
  fun noGrowY()
}

fun <T : JScrollPane> CellBuilder<T>.noGrowY(): CellBuilder<T> {
  (this as ScrollPaneCellBuilder).noGrowY()
  return this
}

fun <T : JTextComponent> CellBuilder<T>.withTextBinding(modelBinding: PropertyBinding<String>): CellBuilder<T> {
  return withBinding(JTextComponent::getText, JTextComponent::setText, modelBinding)
}

fun <T : AbstractButton> CellBuilder<T>.withSelectedBinding(modelBinding: PropertyBinding<Boolean>): CellBuilder<T> {
  return withBinding(AbstractButton::isSelected, AbstractButton::setSelected, modelBinding)
}

val CellBuilder<AbstractButton>.selected
  get() = component.selected

const val UNBOUND_RADIO_BUTTON = "unbound.radio.button"

// separate class to avoid row related methods in the `cell { } `
@CellMarker
abstract class Cell : BaseBuilder {
  /**
   * Sets how keen the component should be to grow in relation to other component **in the same cell**. Use `push` in addition if need.
   * If this constraint is not set the grow weight is set to 0 and the component will not grow (unless some automatic rule is not applied (see [com.intellij.ui.layout.panel])).
   * Grow weight will only be compared against the weights for the same cell.
   */
  val growX = CCFlags.growX

  @Suppress("unused")
  val growY = CCFlags.growY
  val grow = CCFlags.grow

  /**
   * Makes the column that the component is residing in grow with `weight`.
   */
  val pushX = CCFlags.pushX

  /**
   * Makes the row that the component is residing in grow with `weight`.
   */
  @Suppress("unused")
  val pushY = CCFlags.pushY
  val push = CCFlags.push

  fun label(@Label text: String,
            style: UIUtil.ComponentStyle? = null,
            fontColor: UIUtil.FontColor? = null,
            bold: Boolean = false): CellBuilder<JLabel> {
    val label = Label(text, style, fontColor, bold)
    return component(label)
  }

  fun label(@Label text: String,
            font: JBFont,
            fontColor: UIUtil.FontColor? = null): CellBuilder<JLabel> {
    val label = Label(text, fontColor = fontColor, font = font)
    return component(label)
  }

  fun link(@LinkLabel text: String,
           style: UIUtil.ComponentStyle? = null,
           action: () -> Unit): CellBuilder<JComponent> {
    val result = Link(text, style, action)
    return component(result)
  }

  fun browserLink(@LinkLabel text: String, url: String): CellBuilder<JComponent> {
    val result = BrowserLink(text, url)
    return component(result)
  }

  fun buttonFromAction(@Button text: String, @NonNls actionPlace: String, action: AnAction): CellBuilder<JButton> {
    val button = JButton(BundleBase.replaceMnemonicAmpersand(text))
    button.addActionListener { ActionUtil.invokeAction(action, button, actionPlace, null, null) }
    return component(button)
  }

  fun button(@Button text: String, actionListener: (event: ActionEvent) -> Unit): CellBuilder<JButton> {
    val button = JButton(BundleBase.replaceMnemonicAmpersand(text))
    button.addActionListener(actionListener)
    return component(button)
  }

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
  fun checkBox(@Checkbox text: String,
               isSelected: Boolean = false,
               @DetailedDescription comment: String? = null): CellBuilder<JBCheckBox> {
    val result = JBCheckBox(text, isSelected)
    return result(comment = comment)
  }

  fun checkBox(@Checkbox text: String, prop: KMutableProperty0<Boolean>, @DetailedDescription comment: String? = null): CellBuilder<JBCheckBox> {
    return checkBox(text, prop.toBinding(), comment)
  }

  fun checkBox(@Checkbox text: String, getter: () -> Boolean, setter: (Boolean) -> Unit, @DetailedDescription comment: String? = null): CellBuilder<JBCheckBox> {
    return checkBox(text, PropertyBinding(getter, setter), comment)
  }

  private fun checkBox(@Checkbox text: String,
                       modelBinding: PropertyBinding<Boolean>,
                       @DetailedDescription comment: String?): CellBuilder<JBCheckBox> {
    val component = JBCheckBox(text, modelBinding.get())
    return component(comment = comment).withSelectedBinding(modelBinding)
  }

  fun checkBox(@Checkbox text: String,
               property: GraphProperty<Boolean>,
               @DetailedDescription comment: String? = null): CellBuilder<JBCheckBox> {
    val component = JBCheckBox(text, property.get())
    return component(comment = comment).withGraphProperty(property).applyToComponent { component.bind(property) }
  }

  open fun radioButton(@RadioButton text: String, @Nls comment: String? = null): CellBuilder<JBRadioButton> {
    val component = JBRadioButton(text)
    component.putClientProperty(UNBOUND_RADIO_BUTTON, true)
    return component(comment = comment)
  }

  open fun radioButton(@RadioButton text: String, getter: () -> Boolean, setter: (Boolean) -> Unit, @Nls comment: String? = null): CellBuilder<JBRadioButton> {
    val component = JBRadioButton(text, getter())
    return component(comment = comment).withSelectedBinding(PropertyBinding(getter, setter))
  }

  open fun radioButton(@RadioButton text: String, prop: KMutableProperty0<Boolean>, @Nls comment: String? = null): CellBuilder<JBRadioButton> {
    val component = JBRadioButton(text, prop.get())
    return component(comment = comment).withSelectedBinding(prop.toBinding())
  }

  fun <T> comboBox(model: ComboBoxModel<T>,
                   getter: () -> T?,
                   setter: (T?) -> Unit,
                   renderer: ListCellRenderer<T?>? = null): CellBuilder<ComboBox<T>> {
    return comboBox(model, PropertyBinding(getter, setter), renderer)
  }

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

  inline fun <reified T : Any> comboBox(
    model: ComboBoxModel<T>,
    prop: KMutableProperty0<T>,
    renderer: ListCellRenderer<T?>? = null
  ): CellBuilder<ComboBox<T>> {
    return comboBox(model, prop.toBinding().toNullable(), renderer)
  }

  fun <T> comboBox(
    model: ComboBoxModel<T>,
    property: GraphProperty<T>,
    renderer: ListCellRenderer<T?>? = null
  ): CellBuilder<ComboBox<T>> {
    return comboBox(model, PropertyBinding(property::get, property::set).toNullable(), renderer)
      .withGraphProperty(property)
      .applyToComponent { bind(property) }
  }

  fun textField(prop: KMutableProperty0<String>, columns: Int? = null): CellBuilder<JBTextField> = textField(prop.toBinding(), columns)

  fun textField(getter: () -> String, setter: (String) -> Unit, columns: Int? = null) = textField(PropertyBinding(getter, setter), columns)

  fun textField(binding: PropertyBinding<String>, columns: Int? = null): CellBuilder<JBTextField> {
    return component(JBTextField(binding.get(), columns ?: 0))
      .withTextBinding(binding)
  }

  fun textField(property: GraphProperty<String>, columns: Int? = null): CellBuilder<JBTextField> {
    return textField(property::get, property::set, columns)
      .withGraphProperty(property)
      .applyToComponent { bind(property) }
  }

  fun textArea(prop: KMutableProperty0<String>, rows: Int? = null, columns: Int? = null): CellBuilder<JBTextArea> = textArea(prop.toBinding(), rows, columns)

  fun textArea(getter: () -> String, setter: (String) -> Unit, rows: Int? = null, columns: Int? = null) = textArea(PropertyBinding(getter, setter), rows, columns)

  fun textArea(binding: PropertyBinding<String>, rows: Int? = null, columns: Int? = null): CellBuilder<JBTextArea> {
    return component(JBTextArea(binding.get(), rows ?: 0, columns ?: 0))
      .withTextBinding(binding)
  }

  fun textArea(property: GraphProperty<String>, rows: Int? = null, columns: Int? = null): CellBuilder<JBTextArea> {
    return textArea(property::get, property::set, rows, columns)
      .withGraphProperty(property)
      .applyToComponent { bind(property) }
  }

  fun scrollableTextArea(prop: KMutableProperty0<String>, rows: Int? = null): CellBuilder<JBTextArea> = scrollableTextArea(prop.toBinding(), rows)

  fun scrollableTextArea(getter: () -> String, setter: (String) -> Unit, rows: Int? = null) = scrollableTextArea(PropertyBinding(getter, setter), rows)

  fun scrollableTextArea(binding: PropertyBinding<String>, rows: Int? = null): CellBuilder<JBTextArea> {
    val textArea = JBTextArea(binding.get(), rows ?: 0, 0)
    val scrollPane = JBScrollPane(textArea)
    return component(textArea, scrollPane)
      .withTextBinding(binding)
  }

  fun scrollableTextArea(property: GraphProperty<String>, rows: Int? = null): CellBuilder<JBTextArea> {
    return scrollableTextArea(property::get, property::set, rows)
      .withGraphProperty(property)
      .applyToComponent { bind(property) }
  }

  @JvmOverloads
  @ApiStatus.ScheduledForRemoval(inVersion = "2022.2")
  @Deprecated("Use Kotlin UI DSL Version 2")
  fun intTextField(prop: KMutableProperty0<Int>, columns: Int? = null, range: IntRange? = null): CellBuilder<JBTextField> {
    return intTextField(prop, columns, range, null)
  }

  @ApiStatus.ScheduledForRemoval(inVersion = "2022.2")
  @Deprecated("Use Kotlin UI DSL Version 2")
  fun intTextField(prop: KMutableProperty0<Int>, columns: Int? = null, range: IntRange? = null, step: Int? = null): CellBuilder<JBTextField> {
    return intTextField(prop.toBinding(), columns, range, step)
  }

  @JvmOverloads
  @ApiStatus.ScheduledForRemoval(inVersion = "2022.2")
  @Deprecated("Use Kotlin UI DSL Version 2")
  fun intTextField(getter: () -> Int, setter: (Int) -> Unit, columns: Int? = null, range: IntRange? = null, step: Int? = null): CellBuilder<JBTextField> {
    return intTextField(PropertyBinding(getter, setter), columns, range, step)
  }


  @JvmOverloads
  @ApiStatus.ScheduledForRemoval(inVersion = "2022.2")
  @Deprecated("Use Kotlin UI DSL Version 2")
  fun intTextField(binding: PropertyBinding<Int>, columns: Int? = null, range: IntRange? = null): CellBuilder<JBTextField> {
    return intTextField(binding, columns, range, null)
  }

  /**
   * @param step allows changing value by up/down keys on keyboard
   */
  @ApiStatus.ScheduledForRemoval(inVersion = "2022.2")
  @Deprecated("Use Kotlin UI DSL Version 2")
  fun intTextField(binding: PropertyBinding<Int>,
                   columns: Int? = null,
                   range: IntRange? = null,
                   step: Int? = null): CellBuilder<JBTextField> {
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
    }.apply {
      step?.let {
        component.addKeyListener(object : KeyAdapter() {
          override fun keyPressed(e: KeyEvent?) {
            val increment: Int
            when (e?.keyCode) {
              KeyEvent.VK_UP -> increment = step
              KeyEvent.VK_DOWN -> increment = -step
              else -> return
            }

            var value = component.text.toIntOrNull()
            if (value != null) {
              value += increment
              if (range != null) {
                value = MathUtil.clamp(value, range.first, range.last)
              }
              component.text = value.toString()
              e.consume()
            }
          }
        })
      }
    }
  }

  fun spinner(prop: KMutableProperty0<Int>, minValue: Int, maxValue: Int, step: Int = 1): CellBuilder<JBIntSpinner> {
    val spinner = JBIntSpinner(prop.get(), minValue, maxValue, step)
    return component(spinner).withBinding(JBIntSpinner::getNumber, JBIntSpinner::setNumber, prop.toBinding())
  }

  fun spinner(getter: () -> Int, setter: (Int) -> Unit, minValue: Int, maxValue: Int, step: Int = 1): CellBuilder<JBIntSpinner> {
    val spinner = JBIntSpinner(getter(), minValue, maxValue, step)
    return component(spinner).withBinding(JBIntSpinner::getNumber, JBIntSpinner::setNumber, PropertyBinding(getter, setter))
  }

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

  @ApiStatus.ScheduledForRemoval(inVersion = "2022.2")
  @Deprecated("Use Kotlin UI DSL Version 2")
  fun actionButton(action: AnAction, dimension: Dimension = ActionToolbar.DEFAULT_MINIMUM_BUTTON_SIZE): CellBuilder<ActionButton> {
    val actionButton = ActionButton(action, action.templatePresentation, ActionPlaces.UNKNOWN, dimension)
    return actionButton()
  }

  fun gearButton(vararg actions: AnAction): CellBuilder<JComponent> {
    val label = JLabel(LayeredIcon.GEAR_WITH_DROPDOWN)
    label.disabledIcon = AllIcons.General.GearPlain
    object : ClickListener() {
      override fun onClick(e: MouseEvent, clickCount: Int): Boolean {
        if (!label.isEnabled) return true
        JBPopupFactory.getInstance()
          .createActionGroupPopup(null, DefaultActionGroup(*actions), DataContext { dataId ->
            when (dataId) {
              PlatformCoreDataKeys.CONTEXT_COMPONENT.name -> label
              else -> null
            }
          }, true, null, 10)
          .showUnderneathOf(label)
        return true
      }
    }.installOn(label)

    return component(label)
  }

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

  fun expandableTextField(prop: KMutableProperty0<String>,
                          parser: Function<in String, out MutableList<String>> = ParametersListUtil.DEFAULT_LINE_PARSER,
                          joiner: Function<in MutableList<String>, String> = ParametersListUtil.DEFAULT_LINE_JOINER)
    : CellBuilder<ExpandableTextField> {
    return expandableTextField(prop::get, prop::set, parser, joiner)
  }

  fun expandableTextField(prop: GraphProperty<String>,
                          parser: Function<in String, out MutableList<String>> = ParametersListUtil.DEFAULT_LINE_PARSER,
                          joiner: Function<in MutableList<String>, String> = ParametersListUtil.DEFAULT_LINE_JOINER)
    : CellBuilder<ExpandableTextField> {
    return expandableTextField(prop::get, prop::set, parser, joiner)
      .withGraphProperty(prop)
      .applyToComponent { bind(prop) }
  }

  /**
   * @see LayoutBuilder.titledRow
   */
  @JvmOverloads
  fun panel(@BorderTitle title: String, wrappedComponent: Component, hasSeparator: Boolean = true): CellBuilder<JPanel> {
    val panel = Panel(title, hasSeparator)
    panel.add(wrappedComponent)
    return component(panel)
  }

  fun scrollPane(component: Component): CellBuilder<JScrollPane> {
    return component(JBScrollPane(component))
  }

  fun comment(@DetailedDescription text: String, maxLineLength: Int = -1): CellBuilder<JLabel> {
    return component(ComponentPanelBuilder.createCommentComponent(text, true, maxLineLength, true))
  }

  @ApiStatus.ScheduledForRemoval(inVersion = "2022.2")
  @Deprecated("Use Kotlin UI DSL Version 2")
  fun commentNoWrap(@DetailedDescription text: String): CellBuilder<JLabel> {
    return component(ComponentPanelBuilder.createNonWrappingCommentComponent(text))
  }

  @ApiStatus.ScheduledForRemoval(inVersion = "2022.2")
  @Deprecated("Use Kotlin UI DSL Version 2")
  fun placeholder(): CellBuilder<JComponent> {
    return component(JPanel().apply {
      minimumSize = Dimension(0, 0)
      preferredSize = Dimension(0, 0)
      maximumSize = Dimension(0, 0)
    })
  }

  abstract fun <T : JComponent> component(component: T): CellBuilder<T>

  abstract fun <T : JComponent> component(component: T, viewComponent: JComponent): CellBuilder<T>

  operator fun <T : JComponent> T.invoke(
    vararg constraints: CCFlags,
    growPolicy: GrowPolicy? = null,
    @DetailedDescription comment: String? = null
  ): CellBuilder<T> = component(this).apply {
    constraints(*constraints)
    if (comment != null) comment(comment)
    if (growPolicy != null) growPolicy(growPolicy)
  }
}

class InnerCell(val cell: Cell) : Cell() {
  override fun <T : JComponent> component(component: T): CellBuilder<T> {
    return cell.component(component)
  }

  override fun <T : JComponent> component(component: T, viewComponent: JComponent): CellBuilder<T> {
    return cell.component(component, viewComponent)
  }

  @ApiStatus.ScheduledForRemoval(inVersion = "2022.2")
  @Deprecated("Use Kotlin UI DSL Version 2")
  override fun withButtonGroup(title: String?, buttonGroup: ButtonGroup, body: () -> Unit) {
    cell.withButtonGroup(title, buttonGroup, body)
  }
}

fun <T> listCellRenderer(renderer: SimpleListCellRenderer<T?>.(value: T, index: Int, isSelected: Boolean) -> Unit): SimpleListCellRenderer<T?> {
  return object : SimpleListCellRenderer<T?>() {
    override fun customize(list: JList<out T?>, value: T?, index: Int, selected: Boolean, hasFocus: Boolean) {
      if (value != null) {
        renderer(this, value, index, selected)
      }
    }
  }
}

@ApiStatus.ScheduledForRemoval(inVersion = "2022.2")
@Deprecated("Use Kotlin UI DSL Version 2")
fun <C : JTextComponent> C.bindIntProperty(property: ObservableClearableProperty<Int>): C =
  bind(property.transform({ it.toString() }, { it.toInt() }))

@ApiStatus.ScheduledForRemoval(inVersion = "2022.2")
@Deprecated("Use Kotlin UI DSL Version 2")
fun Cell.slider(min: Int, max: Int, minorTick: Int, majorTick: Int): CellBuilder<JSlider> {
  val slider = JSlider()
  UIUtil.setSliderIsFilled(slider, true)
  slider.paintLabels = true
  slider.paintTicks = true
  slider.paintTrack = true
  slider.minimum = min
  slider.maximum = max
  slider.minorTickSpacing = minorTick
  slider.majorTickSpacing = majorTick
  return slider()
}

@ApiStatus.ScheduledForRemoval(inVersion = "2022.2")
@Deprecated("Use Kotlin UI DSL Version 2")
fun <T : JSlider> CellBuilder<T>.labelTable(table: Hashtable<Int, JComponent>.() -> Unit): CellBuilder<T> {
  component.labelTable = Hashtable<Int, JComponent>().apply(table)
  return this
}

@ApiStatus.ScheduledForRemoval(inVersion = "2022.2")
@Deprecated("Use Kotlin UI DSL Version 2")
fun <T : JSlider> CellBuilder<T>.withValueBinding(modelBinding: PropertyBinding<Int>): CellBuilder<T> {
  return withBinding(JSlider::getValue, JSlider::setValue, modelBinding)
}

fun UINumericRange.asRange(): IntRange = min..max