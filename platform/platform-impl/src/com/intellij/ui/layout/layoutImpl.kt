// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.layout

import com.intellij.ui.layout.migLayout.*
import java.awt.Container
import javax.swing.ButtonGroup
import javax.swing.JLabel

// https://jetbrains.github.io/ui/controls/input_field/#spacing
@PublishedApi
internal fun createLayoutBuilder(): LayoutBuilder {
  return LayoutBuilder(MigLayoutBuilder(createIntelliJSpacingConfiguration()))
}

interface LayoutBuilderImpl {
  fun newRow(label: JLabel? = null, buttonGroup: ButtonGroup? = null, separated: Boolean = false): Row

  fun build(container: Container, layoutConstraints: Array<out LCFlags>)

  fun noteRow(text: String, linkHandler: ((url: String) -> Unit)? = null)
}