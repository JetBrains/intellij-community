// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.layout

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.components.JBRadioButton
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import java.awt.event.ActionListener
import javax.swing.AbstractButton
import javax.swing.ButtonGroup

open class LayoutBuilder @PublishedApi internal constructor(@PublishedApi internal val builder: LayoutBuilderImpl) : RowBuilder by builder.rootRow {

  @ApiStatus.ScheduledForRemoval(inVersion = "2022.2")
  @Deprecated("Use Kotlin UI DSL Version 2")
  override fun withButtonGroup(title: String?, buttonGroup: ButtonGroup, body: () -> Unit) {
    builder.withButtonGroup(buttonGroup, body)
  }

  @ApiStatus.ScheduledForRemoval(inVersion = "2022.2")
  @Deprecated("Use Kotlin UI DSL Version 2")
  inline fun buttonGroup(crossinline elementActionListener: () -> Unit, crossinline init: LayoutBuilder.() -> Unit): ButtonGroup {
    val group = ButtonGroup()

    builder.withButtonGroup(group) {
      LayoutBuilder(builder).init()
    }

    val listener = ActionListener { elementActionListener() }
    for (button in group.elements) {
      button.addActionListener(listener)
    }
    return group
  }

  @Suppress("PropertyName")
  @PublishedApi
  @get:Deprecated("", replaceWith = ReplaceWith("builder"), level = DeprecationLevel.ERROR)
  @get:ApiStatus.ScheduledForRemoval(inVersion = "2021.3")
  internal val `$`: LayoutBuilderImpl
    get() = builder
}

@ApiStatus.ScheduledForRemoval(inVersion = "2022.2")
@Deprecated("Use Kotlin UI DSL Version 2")
class CellBuilderWithButtonGroupProperty<T : Any>
@PublishedApi internal constructor(private val prop: PropertyBinding<T>)  {

  @ApiStatus.ScheduledForRemoval(inVersion = "2022.2")
  @Deprecated("Use Kotlin UI DSL Version 2")
  fun Cell.radioButton(@NlsContexts.RadioButton text: String, value: T, @Nls comment: String? = null): CellBuilder<JBRadioButton> {
    val component = JBRadioButton(text, prop.get() == value)
    return component(comment = comment).bindValue(value)
  }

  @ApiStatus.ScheduledForRemoval(inVersion = "2022.2")
  @Deprecated("Use Kotlin UI DSL Version 2")
  fun CellBuilder<JBRadioButton>.bindValue(value: T): CellBuilder<JBRadioButton> = bindValueToProperty(prop, value)
}


class RowBuilderWithButtonGroupProperty<T : Any>
    @PublishedApi internal constructor(private val builder: RowBuilder, private val prop: PropertyBinding<T>) : RowBuilder by builder {

  fun Row.radioButton(@NlsContexts.RadioButton text: String, value: T, @Nls comment: String? = null): CellBuilder<JBRadioButton> {
    val component = JBRadioButton(text, prop.get() == value)
    attachSubRowsEnabled(component)
    return component(comment = comment).bindValue(value)
  }

  fun CellBuilder<JBRadioButton>.bindValue(value: T): CellBuilder<JBRadioButton> = bindValueToProperty(prop, value)
}

private fun <T> CellBuilder<JBRadioButton>.bindValueToProperty(prop: PropertyBinding<T>, value: T): CellBuilder<JBRadioButton> = apply {
  onApply { if (component.isSelected) prop.set(value) }
  onReset { component.isSelected = prop.get() == value }
  onIsModified { component.isSelected != (prop.get() == value) }
}

fun FileChooserDescriptor.chooseFile(event: AnActionEvent, fileChosen: (chosenFile: VirtualFile) -> Unit) {
  FileChooser.chooseFile(this, event.getData(PlatformDataKeys.PROJECT), event.getData(PlatformCoreDataKeys.CONTEXT_COMPONENT), null, fileChosen)
}

fun Row.attachSubRowsEnabled(component: AbstractButton) {
  enableSubRowsIf(component.selected)
}
