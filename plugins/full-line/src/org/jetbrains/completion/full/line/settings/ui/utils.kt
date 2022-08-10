package org.jetbrains.completion.full.line.settings.ui

import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.util.text.StringUtil
import com.intellij.ui.JBCardLayout
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBPasswordField
import com.intellij.ui.layout.*
import org.jetbrains.completion.full.line.models.ModelType
import org.jetbrains.completion.full.line.settings.state.MLServerCompletionSettings
import java.awt.event.ItemEvent
import java.awt.event.ItemListener
import javax.swing.AbstractButton
import kotlin.jvm.internal.CallableReference
import kotlin.reflect.KMutableProperty0
import kotlin.reflect.KMutableProperty1

const val AUTH_TOKEN_UPDATE = "full-line@button-changed"
const val LANGUAGE_CHECKBOX_NAME = "full-line@language-checkbox"

// Required for compatibility with the latest IDEs
fun RowBuilder.fullRow(init: InnerCell.() -> Unit) = row { cell(isFullWidth = true, init = init) }

fun RowBuilder.extended(block: RowBuilder.() -> Unit) {
    if (MLServerCompletionSettings.isExtended()) this.apply(block) else return
}

fun Row.visibleIf(predicate: ComponentPredicate): Row {
    visible = predicate()
    subRowsVisible = predicate()
    predicate.addListener {
        visible = it
        subRowsVisible = it
    }
    return this
}

fun Row.enableSubRowsIf(predicate: ComponentPredicate) {
    subRowsEnabled = predicate()
    predicate.addListener { subRowsEnabled = it }
}

val AbstractButton.visible: ComponentPredicate
    get() = object : ComponentPredicate() {
        override fun invoke(): Boolean = isVisible

        override fun addListener(listener: (Boolean) -> Unit) {
            addChangeListener { listener(isVisible) }
        }
    }


fun CellBuilder<ComboBox<ModelType>>.withModelTypeBinding(modelProperty: KMutableProperty0<ModelType>): CellBuilder<ComboBox<ModelType>> {
    return withBinding(
        { component ->
            if (component.selectedItem != null) {
                component.selectedItem as ModelType
            } else {
                MLServerCompletionSettings.getInstance().getModelMode()
            }
        },
        { component, value -> component.setSelectedItem(value) },
        modelProperty.toBinding()
    )
}

fun CellBuilder<JBPasswordField>.withPasswordBinding(modelProperty: KMutableProperty0<String>): CellBuilder<JBPasswordField> {
    return withBinding(
        { component -> String(component.password) },
        { component, value -> component.text = value },
        modelProperty.toBinding()
    )
}

fun DialogPanel.copyCallbacksFromChild(child: DialogPanel): DialogPanel {
    validateCallbacks = validateCallbacks + child.validateCallbacks
    componentValidateCallbacks = componentValidateCallbacks + child.componentValidateCallbacks

    customValidationRequestors = customValidationRequestors + child.customValidationRequestors
    applyCallbacks = applyCallbacks + child.applyCallbacks
    resetCallbacks = resetCallbacks + child.resetCallbacks
    isModifiedCallbacks = isModifiedCallbacks + child.isModifiedCallbacks
    return this
}

fun DialogPanel.clearCallbacks(): DialogPanel {
    validateCallbacks = emptyList()
    componentValidateCallbacks = emptyMap()

    customValidationRequestors = emptyMap()
    applyCallbacks = emptyMap()
    resetCallbacks = emptyMap()
    isModifiedCallbacks = emptyMap()
    return this
}

fun languageConfigurationKey(language: String, type: ModelType) = "${language}-${type.name}"

fun connectLanguageWithModelType(modelTypeComboBox: ComboBox<ModelType>, languageComboBox: ComboBox<String>, configPanel: DialogPanel) {
    ItemListener {
        if (it.stateChange == ItemEvent.SELECTED) {
            val key = languageConfigurationKey(languageComboBox.selectedItem as String, modelTypeComboBox.selectedItem as ModelType)
            (configPanel.layout as JBCardLayout).show(configPanel, key)
        }
    }.let {
        modelTypeComboBox.addItemListener(it)
        languageComboBox.addItemListener(it)
    }
}

fun DialogPanel.languageCheckboxes() = components.filterIsInstance<JBCheckBox>().filter { it.name == LANGUAGE_CHECKBOX_NAME }

fun JBCheckBox.connectWith(other: JBCheckBox) = addItemListener {
    other.isSelected = isSelected
}

inline fun <V, reified T : Any> KMutableProperty1<V, T>.toBinding(instance: V): PropertyBinding<T> {
    return createPropertyBinding(instance, this, T::class.javaPrimitiveType ?: T::class.java)
}

@Suppress("UNCHECKED_CAST")
fun <V, T> createPropertyBinding(instance: V, prop: KMutableProperty1<V, T>, propType: Class<T>): PropertyBinding<T> {
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
            } catch (e: Exception) {
                // ignore
            }

            try {
                val field = receiverClass.getDeclaredField(name)
                field.isAccessible = true
                return PropertyBinding({ field.get(receiver) as T }, { field.set(receiver, it) })
            } catch (e: Exception) {
                // ignore
            }
        }
    }
    return PropertyBinding({ prop.getter(instance) }, { prop.setter.invoke(instance, it) })
}

fun <T : AbstractButton> CellBuilder<T>.withSelectedBinding(bindings: List<PropertyBinding<Boolean>>): CellBuilder<T> {
    return withBinding(AbstractButton::isSelected, AbstractButton::setSelected, bindings.toBind())
}

fun <T> List<PropertyBinding<T>>.toBind() = PropertyBinding(first().get) { v ->
    forEach { it.set(v) }
}
