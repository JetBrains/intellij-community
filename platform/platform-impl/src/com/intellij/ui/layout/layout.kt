// Copyright 2000-2024 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.layout

import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.util.NlsContexts
import com.intellij.ui.components.DialogPanel
import com.intellij.util.ui.UIUtil
import org.jetbrains.annotations.ApiStatus

/**
 * See [docs](https://plugins.jetbrains.com/docs/intellij/kotlin-ui-dsl.html).
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
@ApiStatus.ScheduledForRemoval
@Deprecated("Use com.intellij.ui.dsl.builder.panel from Kotlin UI DSL Version 2 and kotlin documentations for related classes", level = DeprecationLevel.ERROR)
inline fun panel(vararg constraints: LCFlags, @NlsContexts.DialogTitle title: String? = null, init: LayoutBuilder.() -> Unit): DialogPanel {
  val builder = createLayoutBuilderInternal()
  builder.init()

  val panel = DialogPanel(title, layout = null)
  UIUtil.applyDeprecatedBackground(panel)
  builder.builder.build(panel, constraints)
  initPanelInternal(builder, panel)
  return panel
}

@PublishedApi
@ApiStatus.ScheduledForRemoval
@Deprecated("Use Kotlin UI DSL Version 2", level = DeprecationLevel.ERROR)
internal fun initPanel(builder: LayoutBuilder, panel: DialogPanel) {
  panel.preferredFocusedComponent = builder.builder.preferredFocusedComponent
  panel.validateCallbacks = builder.builder.validateCallbacks
  panel.componentValidateCallbacks = builder.builder.componentValidateCallbacks
}

@ApiStatus.ScheduledForRemoval
@ApiStatus.Internal
@Deprecated("Use Kotlin UI DSL Version 2")
fun initPanelInternal(builder: LayoutBuilder, panel: DialogPanel) {
  panel.preferredFocusedComponent = builder.builder.preferredFocusedComponent
  panel.validateCallbacks = builder.builder.validateCallbacks
  panel.componentValidateCallbacks = builder.builder.componentValidateCallbacks
}
