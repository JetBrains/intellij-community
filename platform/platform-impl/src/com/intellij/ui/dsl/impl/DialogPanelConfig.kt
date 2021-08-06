// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.dsl.impl

import com.intellij.ui.dsl.SpacingConfiguration
import com.intellij.util.ui.JBUI
import org.jetbrains.annotations.ApiStatus
import javax.swing.JComponent

@ApiStatus.Experimental
internal class DialogPanelConfig {

  val spacing = createIntelliJSpacingConfiguration()

  val applyCallbacks: MutableMap<JComponent?, MutableList<() -> Unit>> = linkedMapOf()
  val resetCallbacks: MutableMap<JComponent?, MutableList<() -> Unit>> = linkedMapOf()
  val isModifiedCallbacks: MutableMap<JComponent?, MutableList<() -> Boolean>> = linkedMapOf()
}

// https://jetbrains.github.io/ui/controls/input_field/#spacing
private fun createIntelliJSpacingConfiguration(): SpacingConfiguration {
  return object : SpacingConfiguration {

    override val horizontalUnrelatedGap = JBUI.scale(16)
    override val horizontalIndent = JBUI.scale(20)
    override val verticalComponentGap = JBUI.scale(6)
    override val verticalCommentBottomGap = JBUI.scale(6)
  }
}
