// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.layout

import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.layout.migLayout.*
import java.awt.Container
import javax.swing.ButtonGroup
import javax.swing.JComponent

@PublishedApi
@Deprecated("Use Kotlin UI DSL Version 2")
internal fun createLayoutBuilder(): LayoutBuilder {
  return LayoutBuilder(MigLayoutBuilder(createIntelliJSpacingConfiguration()))
}

@Deprecated("Use Kotlin UI DSL Version 2")
interface LayoutBuilderImpl {
  @Deprecated("Use Kotlin UI DSL Version 2")
  val rootRow: Row

  @Deprecated("Use Kotlin UI DSL Version 2")
  fun withButtonGroup(buttonGroup: ButtonGroup, body: () -> Unit)

  @Deprecated("Use Kotlin UI DSL Version 2")
  fun build(container: Container, layoutConstraints: Array<out LCFlags>)

  @Deprecated("Use Kotlin UI DSL Version 2")
  val preferredFocusedComponent: JComponent?

  // Validators applied when Apply is pressed
  @Deprecated("Use Kotlin UI DSL Version 2")
  val validateCallbacks: List<() -> ValidationInfo?>

  // Validators applied immediately on input
  @Deprecated("Use Kotlin UI DSL Version 2")
  val componentValidateCallbacks: Map<JComponent, () -> ValidationInfo?>

  // Validation applicants for custom validation events
  @Deprecated("Use Kotlin UI DSL Version 2")
  val customValidationRequestors: Map<JComponent, List<(() -> Unit) -> Unit>>

  @Deprecated("Use Kotlin UI DSL Version 2")
  val applyCallbacks: Map<JComponent?, List<() -> Unit>>
  @Deprecated("Use Kotlin UI DSL Version 2")
  val resetCallbacks: Map<JComponent?, List<() -> Unit>>
  @Deprecated("Use Kotlin UI DSL Version 2")
  val isModifiedCallbacks: Map<JComponent?, List<() -> Boolean>>
}