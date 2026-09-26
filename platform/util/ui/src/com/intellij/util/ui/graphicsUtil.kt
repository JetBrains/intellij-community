// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.ui

import java.awt.Graphics2D

inline fun Graphics2D.withTranslated(x: Int, y: Int, block: () -> Unit) {
  translate(x, y)
  try {
    block()
  }
  finally {
    translate(-x, -y)
  }
}

inline fun Graphics2D.withTranslated(x: Double, y: Double, block: () -> Unit) {
  translate(x, y)
  try {
    block()
  }
  finally {
    translate(-x, -y)
  }
}