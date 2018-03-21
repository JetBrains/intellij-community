// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.layout

import com.intellij.ui.layout.migLayout.*
import java.awt.Container
import javax.swing.ButtonGroup
import javax.swing.JLabel

// see com.intellij.uiDesigner.core.AbstractLayout.DEFAULT_HGAP and DEFAULT_VGAP
// https://docs.google.com/document/d/1DKnLkO-7_onA7_NCw669aeMH5ltNvw-QMiQHnXu8k_Y/edit

internal const val HORIZONTAL_GAP = 10
internal const val VERTICAL_GAP = 5

fun createLayoutBuilder() = LayoutBuilder(MigLayoutBuilder())

interface LayoutBuilderImpl {
  fun newRow(label: JLabel? = null, buttonGroup: ButtonGroup? = null, separated: Boolean = false): Row

  fun build(container: Container, layoutConstraints: Array<out LCFlags>)

  fun noteRow(text: String)
}