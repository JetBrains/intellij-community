// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:ApiStatus.Experimental
@file:JvmName("StringsKmp")

package com.intellij.openapi.util.text

import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Contract
import kotlin.jvm.JvmName
import kotlin.jvm.JvmOverloads

@Contract(pure = true)
fun CharSequence.stringHashCode(): Int {
  if (this is String || this is CharSequenceWithStringHash) {
    // we know for sure these classes have conformant (and maybe faster) hashCode()
    return hashCode()
  }

  return this.stringHashCode(0, length)
}

@JvmOverloads
@Contract(pure = true)
fun CharSequence.stringHashCode(from: Int, to: Int, prefixHash: Int = 0): Int {
  var h = prefixHash
  for (off in from..<to) {
    h = 31 * h + get(off).code
  }
  return h
}

@Contract(pure = true)
fun CharArray.stringHashCode(from: Int, to: Int): Int {
  var h = 0
  for (off in from..<to) {
    h = 31 * h + this[off].code
  }
  return h
}