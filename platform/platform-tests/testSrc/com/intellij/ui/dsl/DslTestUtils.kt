// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.dsl

import com.intellij.ui.dsl.gridLayout.GridLayout
import javax.swing.JPanel

fun doLayout(panel: JPanel) {
  doLayout(panel, 800, 600)
}

fun doLayout(panel: JPanel, width: Int, height: Int) {
  panel.setSize(width, height)
  (panel.layout as GridLayout).layoutContainer(panel)
}
