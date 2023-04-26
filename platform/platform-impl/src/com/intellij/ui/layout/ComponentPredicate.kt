// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.layout

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.options.advanced.AdvancedSettings
import com.intellij.openapi.options.advanced.AdvancedSettingsChangeListener
import com.intellij.ui.DocumentAdapter
import javax.swing.AbstractButton
import javax.swing.JComboBox
import javax.swing.JTextField
import javax.swing.event.DocumentEvent
import javax.swing.text.JTextComponent

abstract class ComponentPredicate : () -> Boolean {
  abstract fun addListener(listener: (Boolean) -> Unit)

  companion object {
    val TRUE: ComponentPredicate = ConstantComponentPredicate(true)
    val FALSE: ComponentPredicate = ConstantComponentPredicate(false)
  }
}

val AbstractButton.selected: ComponentPredicate
    get() = object : ComponentPredicate() {
      override fun invoke(): Boolean = isSelected

      override fun addListener(listener: (Boolean) -> Unit) {
        addChangeListener { listener(isSelected) }
      }
    }

fun <T> JComboBox<T>.selectedValueMatches(predicate: (T?) -> Boolean): ComponentPredicate {
  return ComboBoxPredicate(this, predicate)
}

class ComboBoxPredicate<T>(private val comboBox: JComboBox<T>, private val predicate: (T?) -> Boolean) : ComponentPredicate() {
  override fun invoke(): Boolean = predicate(comboBox.selectedItem as T?)

  override fun addListener(listener: (Boolean) -> Unit) {
    comboBox.addActionListener {
      listener(predicate(comboBox.selectedItem as T?))
    }
  }
}

/**
 * Used for editable ComboBoxes
 */
fun JComboBox<*>.editableValueMatches(predicate: (Any?) -> Boolean): ComponentPredicate {
  return EditableComboBoxPredicate(this, predicate)
}

private class EditableComboBoxPredicate(private val comboBox: JComboBox<*>, private val predicate: (Any?) -> Boolean) : ComponentPredicate() {
  override fun invoke(): Boolean = predicate(comboBox.editor.item)

  override fun addListener(listener: (Boolean) -> Unit) {
    val textField = comboBox.editor.editorComponent as JTextField
    textField.document.addDocumentListener(object : DocumentAdapter() {
      override fun textChanged(e: DocumentEvent) {
        listener(invoke())
      }
    })
  }
}

fun JTextComponent.enteredTextSatisfies(predicate: (String) -> Boolean): ComponentPredicate {
  return TextComponentPredicate(this, predicate)
}

private class TextComponentPredicate(private val component: JTextComponent, private val predicate: (String) -> Boolean) : ComponentPredicate() {
  override fun invoke(): Boolean = predicate(component.text)

  override fun addListener(listener: (Boolean) -> Unit) {
    component.document.addDocumentListener(object : DocumentAdapter() {
      override fun textChanged(e: DocumentEvent) {
        listener(invoke())
      }
    })
  }
}

fun <T> JComboBox<T>.selectedValueIs(value: T): ComponentPredicate = selectedValueMatches { it == value }

infix fun ComponentPredicate.and(other: ComponentPredicate): ComponentPredicate {
  return AndPredicate(this, other)
}

infix fun ComponentPredicate.or(other: ComponentPredicate): ComponentPredicate {
  return OrPredicate(this, other)
}

operator fun ComponentPredicate.not() : ComponentPredicate {
  return NotPredicate(this)
}

private class AndPredicate(private val lhs: ComponentPredicate, private val rhs: ComponentPredicate) : ComponentPredicate() {
  override fun invoke(): Boolean = lhs.invoke() && rhs.invoke()

  override fun addListener(listener: (Boolean) -> Unit) {
    val andListener: (Boolean) -> Unit = { listener(lhs.invoke() && rhs.invoke()) }
    lhs.addListener(andListener)
    rhs.addListener(andListener)
  }
}

private class OrPredicate(private val lhs: ComponentPredicate, private val rhs: ComponentPredicate) : ComponentPredicate() {
  override fun invoke(): Boolean = lhs.invoke() || rhs.invoke()

  override fun addListener(listener: (Boolean) -> Unit) {
    val andListener: (Boolean) -> Unit = { listener(lhs.invoke() || rhs.invoke()) }
    lhs.addListener(andListener)
    rhs.addListener(andListener)
  }
}

private class NotPredicate(private val that: ComponentPredicate) : ComponentPredicate() {
  override fun invoke(): Boolean = !that.invoke()

  override fun addListener(listener: (Boolean) -> Unit) {
    val notListener: (Boolean) -> Unit = { listener(!that.invoke()) }
    that.addListener(notListener)
  }
}

private class ConstantComponentPredicate(private val value: Boolean) : ComponentPredicate() {
  override fun addListener(listener: (Boolean) -> Unit) = Unit

  override fun invoke(): Boolean = value
}

class AdvancedSettingsPredicate(val id: String, val disposable: Disposable) : ComponentPredicate() {
  override fun addListener(listener: (Boolean) -> Unit) {
    ApplicationManager.getApplication().messageBus.connect(disposable)
      .subscribe(AdvancedSettingsChangeListener.TOPIC, object : AdvancedSettingsChangeListener {
        override fun advancedSettingChanged(id: String, oldValue: Any, newValue: Any) {
          listener(invoke())
        }
      })
  }

  override fun invoke(): Boolean = AdvancedSettings.getBoolean(id)
}