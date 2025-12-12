// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.syntax.util.runtime.impl

internal class Variant {
  var position: Int = 0
  var `object`: Any? = null

  fun init(pos: Int, o: Any?): Variant {
    position = pos
    `object` = o
    return this
  }

  override fun toString(): String {
    return "<$position, $`object`>"
  }
}