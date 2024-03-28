// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.ui.components

import com.intellij.util.ui.JBFont
import javax.swing.border.Border

interface JBComponent<T : JBComponent<T>> {
  fun withBorder(border: Border): T
  fun withFont(font: JBFont): T
  fun andTransparent(): T
  fun andOpaque(): T
}