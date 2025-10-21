// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.dsl.builder.impl

import com.intellij.ui.DocumentAdapter
import com.intellij.ui.components.DropDownLink
import com.intellij.ui.dsl.UiDslException
import com.intellij.ui.dsl.builder.ChangeContext
import org.jetbrains.annotations.ApiStatus
import java.awt.event.ItemEvent
import javax.swing.AbstractButton
import javax.swing.JComboBox
import javax.swing.JComponent
import javax.swing.JSlider
import javax.swing.JSpinner
import javax.swing.event.DocumentEvent
import javax.swing.text.JTextComponent

@ApiStatus.Internal
internal class OnChangeManager<T : JComponent>(private val component: T) {

  private var binding = false

  @Throws(UiDslException::class)
  fun register(listener: (component: T, context: ChangeContext) -> Unit) {
    when (val interactiveComponent = component.interactiveComponent) {
      is DropDownLink<*> ->
        interactiveComponent.addItemListener {
          if (it.stateChange == ItemEvent.SELECTED) {
            listener(component, ChangeContext(it, binding))
          }
        }
      is AbstractButton -> interactiveComponent.addItemListener { listener(component, ChangeContext(it, binding)) }
      is JTextComponent -> interactiveComponent.document.addDocumentListener(object : DocumentAdapter() {
        override fun textChanged(e: DocumentEvent) {
          listener(component, ChangeContext(e, binding))
        }
      })
      is JComboBox<*> ->
        interactiveComponent.addItemListener {
          if (it.stateChange == ItemEvent.SELECTED) {
            listener(component, ChangeContext(it, binding))
          }
        }
      is JSlider ->
        interactiveComponent.addChangeListener {
          listener(component, ChangeContext(it, binding))
        }
      is JSpinner -> throw UiDslException("Hard to support ${component::class.java.name}")
      else -> throw UiDslException("Not yet supported component type ${component::class.java.name}")
    }
  }

  fun applyBinding(init: () -> Unit) {
    binding = true
    try {
      init()
    }
    finally {
      binding = false
    }
  }
}
