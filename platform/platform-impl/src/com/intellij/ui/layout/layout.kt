// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.layout

import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.util.NlsContexts
import com.intellij.ui.components.DialogPanel

/**
 * See [docs](http://www.jetbrains.org/intellij/sdk/docs/user_interface_components/kotlin_ui_dsl.html).
 *
 * Claims all available space in the container for the columns ([LCFlags.fillX], if `constraints` is passed, `fillX` will be not applied - add it explicitly if need).
 * At least one component need to have a [Row.grow] constraint for it to fill the container.
 *
 * Check `Tools -> Internal Actions -> UI -> UI DSL Debug Mode` to turn on debug painting.
 *
 * `JTextComponent`, `TextFieldWithHistory` (use [Row.textFieldWithBrowseButton]), `SeparatorComponent` and `ComponentWithBrowseButton` components automatically have [Row.growX].
 *
 * `ToolbarDecorator` and `JBScrollPane` (use [Row.scrollPane]) components automatically have [Row.grow] and [Row.push].
 */
@Deprecated("Use com.intellij.ui.dsl.builder.panel from Kotlin UI DSL Version 2 and kotlin documentations for related classes")
inline fun panel(vararg constraints: LCFlags, @NlsContexts.DialogTitle title: String? = null, init: LayoutBuilder.() -> Unit): DialogPanel {
  val builder = createLayoutBuilder()
  builder.init()

  val panel = DialogPanel(title, layout = null)
  builder.builder.build(panel, constraints)
  initPanel(builder, panel)
  return panel
}

@PublishedApi
internal fun initPanel(builder: LayoutBuilder, panel: DialogPanel) {
  panel.preferredFocusedComponent = builder.builder.preferredFocusedComponent
  panel.validateCallbacks = builder.builder.validateCallbacks
  panel.componentValidateCallbacks = builder.builder.componentValidateCallbacks
  panel.customValidationRequestors = builder.builder.customValidationRequestors
  panel.applyCallbacks = builder.builder.applyCallbacks
  panel.resetCallbacks = builder.builder.resetCallbacks
  panel.isModifiedCallbacks = builder.builder.isModifiedCallbacks
}