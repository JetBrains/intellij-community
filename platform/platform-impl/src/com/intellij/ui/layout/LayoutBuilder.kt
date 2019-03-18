// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.layout

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.components.JBRadioButton
import com.intellij.ui.components.Label
import com.intellij.ui.layout.migLayout.*
import java.awt.event.ActionListener
import javax.swing.ButtonGroup
import javax.swing.JLabel
import kotlin.reflect.KMutableProperty0
import kotlin.reflect.KProperty0

open class LayoutBuilder @PublishedApi internal constructor(@PublishedApi internal val builder: LayoutBuilderImpl, val buttonGroup: ButtonGroup? = null) {
  inline fun row(label: String, init: Row.() -> Unit) = row(label = Label(label), init = init)

  inline fun row(label: JLabel? = null, separated: Boolean = false, init: Row.() -> Unit): Row {
    val row = builder.newRow(label, buttonGroup, separated)
    row.init()
    return row
  }

  inline fun titledRow(title: String, init: Row.() -> Unit): Row {
    val row = builder.newTitledRow(title)
    row.init()
    return row
  }

  // linkHandler is not an optional for backward compatibility
  /**
   * Hyperlinks are supported (`<a href=""></a>`), new lines and <br> are supported only if no links (file issue if need).
   */
  @JvmOverloads
  fun noteRow(text: String, linkHandler: ((url: String) -> Unit)? = null) {
    builder.noteRow(text, linkHandler)
  }

  fun commentRow(text: String) {
    builder.commentRow(text)
  }

  inline fun buttonGroup(init: LayoutBuilder.() -> Unit) {
    LayoutBuilder(builder, ButtonGroup()).init()
  }

  fun <T : Any> buttonGroup(prop: KMutableProperty0<T>, init: LayoutBuilderWithButtonGroupProperty<T>.() -> Unit) {
    LayoutBuilderWithButtonGroupProperty(builder, prop).init()
  }

  inline fun buttonGroup(crossinline elementActionListener: () -> Unit, init: LayoutBuilder.() -> Unit): ButtonGroup {
    val group = ButtonGroup()
    LayoutBuilder(builder, group).init()

    val listener = ActionListener { elementActionListener() }
    for (button in group.elements) {
      button.addActionListener(listener)
    }
    return group
  }

  inline fun <T : Any> buttonGroup(propertyManager: ChoicePropertyUiManager<T>, init: LayoutBuilderWithButtonGroup<T>.() -> Unit) {
    LayoutBuilderWithButtonGroup(builder, propertyManager).init()
  }

  @Suppress("PropertyName")
  @PublishedApi
  @Deprecated("", replaceWith = ReplaceWith("builder"), level = DeprecationLevel.ERROR)
  internal val `$`: LayoutBuilderImpl
    get() = builder
}

@Suppress("unused")
class LayoutBuilderWithButtonGroup<T : Any> @PublishedApi internal constructor(builder: LayoutBuilderImpl, internal val propertyManager: ChoicePropertyUiManager<T>) : LayoutBuilder(builder)

class LayoutBuilderWithButtonGroupProperty<T : Any>
    @PublishedApi internal constructor(builder: LayoutBuilderImpl, private val prop: KMutableProperty0<T>) : LayoutBuilder(builder, ButtonGroup()) {

  fun Row.radioButton(text: String, value: T): CellBuilder<JBRadioButton> {
    val component = JBRadioButton(text, prop.get() == value)
    return component()
      .onApply { if (component.isSelected) prop.set(value) }
  }
}

fun FileChooserDescriptor.chooseFile(event: AnActionEvent, fileChosen: (chosenFile: VirtualFile) -> Unit) {
  FileChooser.chooseFile(this, event.getData(PlatformDataKeys.PROJECT), event.getData(PlatformDataKeys.CONTEXT_COMPONENT), null, fileChosen)
}