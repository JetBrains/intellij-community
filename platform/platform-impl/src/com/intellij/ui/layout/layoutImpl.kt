// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.layout

import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.layout.migLayout.*
import org.jetbrains.annotations.ApiStatus
import java.awt.Container
import javax.swing.ButtonGroup
import javax.swing.JComponent

@PublishedApi
internal fun createLayoutBuilder(): LayoutBuilder {
  return LayoutBuilder(MigLayoutBuilder(createIntelliJSpacingConfiguration()))
}

@Suppress("DeprecatedCallableAddReplaceWith")
@PublishedApi
@Deprecated(message = "isUseMagic not used anymore")
@ApiStatus.ScheduledForRemoval(inVersion = "2021.3")
internal fun createLayoutBuilder(isUseMagic: Boolean) = createLayoutBuilder()

interface LayoutBuilderImpl {
  val rootRow: Row
  fun withButtonGroup(buttonGroup: ButtonGroup, body: () -> Unit)

  fun build(container: Container, layoutConstraints: Array<out LCFlags>)

  val preferredFocusedComponent: JComponent?

  // Validators applied when Apply is pressed
  val validateCallbacks: List<() -> ValidationInfo?>

  // Validators applied immediately on input
  val componentValidateCallbacks: Map<JComponent, () -> ValidationInfo?>

  // Validation applicants for custom validation events
  val customValidationRequestors: Map<JComponent, List<(() -> Unit) -> Unit>>

  val applyCallbacks: Map<JComponent?, List<() -> Unit>>
  val resetCallbacks: Map<JComponent?, List<() -> Unit>>
  val isModifiedCallbacks: Map<JComponent?, List<() -> Boolean>>
}