// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("StringUtilKmp")
@file:ApiStatus.Experimental

package com.intellij.openapi.util.text

import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Contract
import kotlin.jvm.JvmName

@Contract(pure = true)
fun CharSequence.containsLineBreak(): Boolean {
  for (i in 0..<length) {
    val c = this[i]
    if (c.isLineBreak()) return true
  }
  return false
}

@Contract(pure = true)
fun Char.isLineBreak(): Boolean = this == '\n' || this == '\r'

@Contract(pure = true)
fun CharSequence.getLineBreakCount(): Int {
  var count = 0
  var i = 0
  while (i < length) {
    val c = this[i]
    if (c == '\n') {
      count++
    }
    else if (c == '\r') {
      if (i + 1 < length && this[i + 1] == '\n') {
        i++
      }
      count++
    }
    i++
  }
  return count
}