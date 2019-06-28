// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.layout

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.components.JBRadioButton
import java.awt.event.ActionListener
import javax.swing.ButtonGroup
import javax.swing.JComponent
import javax.swing.JLabel
import kotlin.reflect.KMutableProperty0

open class LayoutBuilder @PublishedApi internal constructor(@PublishedApi internal val builder: LayoutBuilderImpl, override val buttonGroup: ButtonGroup? = null) : RowBuilder {
  override fun createChildRow(label: JLabel?, buttonGroup: ButtonGroup?, isSeparated: Boolean, noGrid: Boolean, title: String?): Row {
    return builder.rootRow.createChildRow(label, buttonGroup, isSeparated, noGrid, title)
  }

  override fun createNoteOrCommentRow(component: JComponent): Row {
    return builder.rootRow.createNoteOrCommentRow(component)
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

  @Suppress("PropertyName")
  @PublishedApi
  @Deprecated("", replaceWith = ReplaceWith("builder"), level = DeprecationLevel.ERROR)
  internal val `$`: LayoutBuilderImpl
    get() = builder
}

class LayoutBuilderWithButtonGroupProperty<T : Any>
    @PublishedApi internal constructor(builder: LayoutBuilderImpl, private val prop: KMutableProperty0<T>) : LayoutBuilder(builder, ButtonGroup()) {

  fun Row.radioButton(text: String, value: T): CellBuilder<JBRadioButton> {
    val component = JBRadioButton(text, prop.get() == value)
    subRowsEnabled = component.isSelected
    component.addChangeListener {
      subRowsEnabled = component.isSelected
    }
    return component()
      .onApply { if (component.isSelected) prop.set(value) }
      .onReset { component.isSelected = prop.get() == value }
      .onIsModified { component.isSelected != (prop.get() == value) }
  }
}

fun FileChooserDescriptor.chooseFile(event: AnActionEvent, fileChosen: (chosenFile: VirtualFile) -> Unit) {
  FileChooser.chooseFile(this, event.getData(PlatformDataKeys.PROJECT), event.getData(PlatformDataKeys.CONTEXT_COMPONENT), null, fileChosen)
}