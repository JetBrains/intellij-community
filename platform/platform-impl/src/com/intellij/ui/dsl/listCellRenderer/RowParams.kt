// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.dsl.listCellRenderer

import java.awt.Color
import java.awt.Component
import javax.swing.border.Border

interface RowParams {

  var border: Border?

  var background: Color?

  /**
   * Component that overrides accessible context for the row
   */
  var accessibleContextProvider: Component?
}