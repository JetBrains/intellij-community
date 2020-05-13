// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.layout

import com.intellij.BundleBase
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.observable.properties.GraphProperty
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.ui.panel.ComponentPanelBuilder
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.*
import com.intellij.ui.components.*
import com.intellij.ui.components.fields.ExpandableTextField
import com.intellij.util.Function
import com.intellij.util.execution.ParametersListUtil
import com.intellij.util.ui.UIUtil
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import java.awt.Component
import java.awt.Dimension
import java.awt.event.ActionEvent
import java.awt.event.ActionListener
import java.awt.event.ItemEvent
import java.awt.event.MouseEvent
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.*
import javax.swing.event.DocumentEvent
import kotlin.jvm.internal.CallableReference
import kotlin.reflect.KMutableProperty0

@DslMarker
annotation class CellMarker

data class PropertyBinding<V>(val get: () -> V, val set: (V) -> Unit)

@PublishedApi
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
  fun error(@Nls message: String): ValidationInfo = ValidationInfo(message, component)
  fun warning(@Nls message: String): ValidationInfo = ValidationInfo(message, component).asWarning().withOKEnabled()
}

interface CellBuilder<out T : JComponent> {
  val component: T

  fun comment(text: String, maxLineLength: Int = 70): CellBuilder<T>
  fun commentComponent(text: String, maxLineLength: Int = 70): CellBuilder<T>
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
  fun sizeGroup(name: String): CellBuilder<T>
  fun growPolicy(growPolicy: GrowPolicy): CellBuilder<T>
  fun constraints(vararg constraints: CCFlags): CellBuilder<T>

  /**
   * If this method is called, the value of the component will be stored to the backing property only if the component is enabled.
   */
  fun applyIfEnabled(): CellBuilder<T>

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

  fun withErrorOnApplyIf(message: String, callback: (T) -> Boolean): CellBuilder<T> {
    withValidationOnApply { if (callback(it)) error(message) else null }
    return this
  }

  @ApiStatus.Internal
  fun shouldSaveOnApply(): Boolean

  fun withLargeLeftGap(): CellBuilder<T>

  @Deprecated("Prefer not to use hardcoded values")
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

fun <T : JTextField> CellBuilder<T>.withTextBinding(modelBinding: PropertyBinding<String>): CellBuilder<T> {
  return withBinding(JTextField::getText, JTextField::setText, modelBinding)
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

  fun label(@Nls text: String,
            style: UIUtil.ComponentStyle? = null,
            fontColor: UIUtil.FontColor? = null,
            bold: Boolean = false): CellBuilder<JLabel> {
    val label = Label(text, style, fontColor, bold)
    return component(label)
  }

  fun link(@Nls text: String,
           style: UIUtil.ComponentStyle? = null,
           action: () -> Unit): CellBuilder<JComponent> {
    val result = Link(text, action = action)
    style?.let { UIUtil.applyStyle(it, result) }
    return component(result)
  }

  fun browserLink(@Nls text: String, url: String): CellBuilder<JComponent> {
    val result = HyperlinkLabel()
    result.setHyperlinkText(text)
    result.setHyperlinkTarget(url)
    return component(result)
  }

  fun buttonFromAction(@Nls text: String, actionPlace: String, action: AnAction): CellBuilder<JButton> {
    val button = JButton(BundleBase.replaceMnemonicAmpersand(text))
    button.addActionListener { ActionUtil.invokeAction(action, button, actionPlace, null, null) }
    return component(button)
  }

  fun button(@Nls text: String, actionListener: (event: ActionEvent) -> Unit): CellBuilder<JButton> {
    val button = JButton(BundleBase.replaceMnemonicAmpersand(text))
    button.addActionListener(actionListener)
    return component(button)
  }

  inline fun checkBox(@Nls text: String,
                      isSelected: Boolean = false,
                      comment: String? = null,
                      crossinline actionListener: (event: ActionEvent, component: JCheckBox) -> Unit): CellBuilder<JBCheckBox> {
    return checkBox(text, isSelected, comment)
      .applyToComponent {
        addActionListener(ActionListener { actionListener(it, this) })
      }
  }

  @JvmOverloads
  fun checkBox(@Nls text: String,
               isSelected: Boolean = false,
               comment: String? = null): CellBuilder<JBCheckBox> {
    val result = JBCheckBox(text, isSelected)
    return result(comment = comment)
  }

  fun checkBox(@Nls text: String, prop: KMutableProperty0<Boolean>, comment: String? = null): CellBuilder<JBCheckBox> {
    return checkBox(text, prop.toBinding(), comment)
  }

  fun checkBox(@Nls text: String, getter: () -> Boolean, setter: (Boolean) -> Unit, comment: String? = null): CellBuilder<JBCheckBox> {
    return checkBox(text, PropertyBinding(getter, setter), comment)
  }

  private fun checkBox(@Nls text: String,
                       modelBinding: PropertyBinding<Boolean>,
                       comment: String?): CellBuilder<JBCheckBox> {
    val component = JBCheckBox(text, modelBinding.get())
    return component(comment = comment).withSelectedBinding(modelBinding)
  }

  fun checkBox(@Nls text: String,
               property: GraphProperty<Boolean>,
               comment: String? = null): CellBuilder<JBCheckBox> {
    val component = JBCheckBox(text, property.get())
    return component(comment = comment).withGraphProperty(property).applyToComponent { component.bind(property) }
  }

  open fun radioButton(@Nls text: String, @Nls comment: String? = null): CellBuilder<JBRadioButton> {
    val component = JBRadioButton(text)
    component.putClientProperty(UNBOUND_RADIO_BUTTON, true)
    return component(comment = comment)
  }

  open fun radioButton(@Nls text: String, getter: () -> Boolean, setter: (Boolean) -> Unit, @Nls comment: String? = null): CellBuilder<JBRadioButton> {
    val component = JBRadioButton(text, getter())
    return component(comment = comment).withSelectedBinding(PropertyBinding(getter, setter))
  }

  open fun radioButton(@Nls text: String, prop: KMutableProperty0<Boolean>, @Nls comment: String? = null): CellBuilder<JBRadioButton> {
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

  fun intTextField(prop: KMutableProperty0<Int>, columns: Int? = null, range: IntRange? = null): CellBuilder<JBTextField> {
    return intTextField(prop.toBinding(), columns, range)
  }

  fun intTextField(getter: () -> Int, setter: (Int) -> Unit, columns: Int? = null, range: IntRange? = null): CellBuilder<JBTextField> {
    return intTextField(PropertyBinding(getter, setter), columns, range)
  }

  fun intTextField(binding: PropertyBinding<Int>, columns: Int? = null, range: IntRange? = null): CellBuilder<JBTextField> {
    return textField(
      { binding.get().toString() },
      { value -> value.toIntOrNull()?.let { intValue -> binding.set(range?.let { intValue.coerceIn(it.first, it.last) } ?: intValue) } },
      columns
    ).withValidationOnInput {
      val value = it.text.toIntOrNull()
      when {
        value == null -> error("Please enter a number")
        range != null && value !in range -> error("Please enter a number from ${range.first} to ${range.last}")
        else -> null
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
    browseDialogTitle: String,
    value: String? = null,
    project: Project? = null,
    fileChooserDescriptor: FileChooserDescriptor = FileChooserDescriptorFactory.createSingleFileNoJarsDescriptor(),
    historyProvider: (() -> List<String>)? = null,
    fileChosen: ((chosenFile: VirtualFile) -> String)? = null
  ): CellBuilder<TextFieldWithHistoryWithBrowseButton> {
    val textField = textFieldWithHistoryWithBrowseButton(project, browseDialogTitle, fileChooserDescriptor, historyProvider, fileChosen)
    if (value != null) textField.text = value
    return component(textField)
  }

  fun textFieldWithBrowseButton(
    browseDialogTitle: String? = null,
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
    browseDialogTitle: String? = null,
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
    browseDialogTitle: String? = null,
    project: Project? = null,
    fileChooserDescriptor: FileChooserDescriptor = FileChooserDescriptorFactory.createSingleFileNoJarsDescriptor(),
    fileChosen: ((chosenFile: VirtualFile) -> String)? = null
  ): CellBuilder<TextFieldWithBrowseButton> {
    val modelBinding = PropertyBinding(getter, setter)
    return textFieldWithBrowseButton(modelBinding, browseDialogTitle, project, fileChooserDescriptor, fileChosen)
  }

  fun textFieldWithBrowseButton(
    modelBinding: PropertyBinding<String>,
    browseDialogTitle: String? = null,
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
    browseDialogTitle: String? = null,
    project: Project? = null,
    fileChooserDescriptor: FileChooserDescriptor = FileChooserDescriptorFactory.createSingleFileNoJarsDescriptor(),
    fileChosen: ((chosenFile: VirtualFile) -> String)? = null
  ): CellBuilder<TextFieldWithBrowseButton> {
    return textFieldWithBrowseButton(property::get, property::set, browseDialogTitle, project, fileChooserDescriptor, fileChosen)
      .withGraphProperty(property)
      .applyToComponent { bind(property) }
  }

  fun gearButton(vararg actions: AnAction): CellBuilder<JComponent> {
    val label = JLabel(LayeredIcon(AllIcons.General.GearPlain, AllIcons.General.Dropdown))
    label.disabledIcon = AllIcons.General.GearPlain
    object : ClickListener() {
      override fun onClick(e: MouseEvent, clickCount: Int): Boolean {
        if (!label.isEnabled) return true
        JBPopupFactory.getInstance()
          .createActionGroupPopup(null, DefaultActionGroup(*actions), DataContext { dataId ->
            when (dataId) {
              PlatformDataKeys.CONTEXT_COMPONENT.name -> label
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
  fun panel(title: String, wrappedComponent: Component, hasSeparator: Boolean = true): CellBuilder<JPanel> {
    val panel = Panel(title, hasSeparator)
    panel.add(wrappedComponent)
    return component(panel)
  }

  fun scrollPane(component: Component): CellBuilder<JScrollPane> {
    return component(JBScrollPane(component))
  }

  fun comment(text: String, maxLineLength: Int = -1): CellBuilder<JLabel> {
    return component(ComponentPanelBuilder.createCommentComponent(text, true, maxLineLength, true))
  }

  fun commentNoWrap(text: String): CellBuilder<JLabel> {
    return component(ComponentPanelBuilder.createNonWrappingCommentComponent(text))
  }

  fun placeholder(): CellBuilder<JComponent> {
    return component(JPanel().apply {
      minimumSize = Dimension(0, 0)
      preferredSize = Dimension(0, 0)
      maximumSize = Dimension(0, 0)
    })
  }

  abstract fun <T : JComponent> component(component: T): CellBuilder<T>

  operator fun <T : JComponent> T.invoke(
    vararg constraints: CCFlags,
    growPolicy: GrowPolicy? = null,
    comment: String? = null
  ): CellBuilder<T> = component(this).apply {
    constraints(*constraints)
    if (comment != null) comment(comment)
    if (growPolicy != null) growPolicy(growPolicy)
  }
}

private fun JBCheckBox.bind(property: GraphProperty<Boolean>) {
  val mutex = AtomicBoolean()
  property.afterChange {
    mutex.lockOrSkip {
      isSelected = property.get()
    }
  }
  addItemListener {
    mutex.lockOrSkip {
      property.set(isSelected)
    }
  }
}

class InnerCell(val cell: Cell) : Cell() {
  override fun <T : JComponent> component(component: T): CellBuilder<T> {
    return cell.component(component)
  }

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

private fun <T> ComboBox<T>.bind(property: GraphProperty<T>) {
  val mutex = AtomicBoolean()
  property.afterChange {
    mutex.lockOrSkip {
      selectedItem = it
    }
  }
  addItemListener {
    if (it.stateChange == ItemEvent.SELECTED) {
      mutex.lockOrSkip {
        @Suppress("UNCHECKED_CAST")
        property.set(it.item as T)
      }
    }
  }
}

private fun TextFieldWithBrowseButton.bind(property: GraphProperty<String>) {
  textField.bind(property)
}

private fun JTextField.bind(property: GraphProperty<String>) {
  val mutex = AtomicBoolean()
  property.afterChange {
    mutex.lockOrSkip {
      text = it
    }
  }
  document.addDocumentListener(
    object : DocumentAdapter() {
      override fun textChanged(e: DocumentEvent) {
        mutex.lockOrSkip {
          property.set(text)
        }
      }
    }
  )
}

private fun AtomicBoolean.lockOrSkip(action: () -> Unit) {
  if (!compareAndSet(false, true)) return
  try {
    action()
  }
  finally {
    set(false)
  }
}

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

fun <T : JSlider> CellBuilder<T>.labelTable(table: Hashtable<Int, JComponent>.() -> Unit): CellBuilder<T> {
  component.labelTable = Hashtable<Int, JComponent>().apply(table)
  return this
}

fun <T : JSlider> CellBuilder<T>.withValueBinding(modelBinding: PropertyBinding<Int>): CellBuilder<T> {
  return withBinding(JSlider::getValue, JSlider::setValue, modelBinding)
}