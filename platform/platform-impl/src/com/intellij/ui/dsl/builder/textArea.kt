// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.dsl.builder

import javax.swing.JTextArea

fun <T : JTextArea> Cell<T>.columns(columns: Int): Cell<T> {
  component.columns = columns
  return this
}

fun <T : JTextArea> Cell<T>.rows(rows: Int): Cell<T> {
  component.rows = rows
  return this
}
