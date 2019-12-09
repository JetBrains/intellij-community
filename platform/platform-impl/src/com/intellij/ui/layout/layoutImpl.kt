// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.layout

import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.layout.migLayout.*
import java.awt.Container
import javax.swing.ButtonGroup
import javax.swing.JComponent

@PublishedApi
@JvmOverloads
internal fun createLayoutBuilder(isUseMagic: Boolean = true /* preserved for API compatibility */): LayoutBuilder {
  return LayoutBuilder(MigLayoutBuilder(createIntelliJSpacingConfiguration()))
}

interface LayoutBuilderImpl {
  val rootRow: Row
  fun withButtonGroup(buttonGroup: ButtonGroup, body: () -> Unit)

  fun build(container: Container, layoutConstraints: Array<out LCFlags>)

  val preferredFocusedComponent: JComponent?

  // Validators applied when Apply is pressed
  val validateCallbacks: List<() -> ValidationInfo?>

  // Validators applied immediately on input
  val componentValidateCallbacks: Map<JComponent, () -> ValidationInfo?>

  val applyCallbacks: List<() -> Unit>
  val resetCallbacks: List<() -> Unit>
  val isModifiedCallbacks: List<() -> Boolean>
}