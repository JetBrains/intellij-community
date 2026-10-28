// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl

import java.text.BreakIterator
import java.text.CharacterIterator

internal class BreakAtEveryCharacterIterator : BreakIterator() {
  private var start: Int = 0
  private var end: Int = 0
  private var current: Int = 0

  fun setRange(start: Int, end: Int) {
    this.start = start
    this.end = end
  }

  override fun first(): Int {
    return start.also { current = it }
  }

  override fun next(): Int {
    return if (current >= end) DONE else ++current
  }

  override fun current(): Int {
    return current
  }

  override fun last(): Int {
    throw UnsupportedOperationException()
  }

  override fun next(n: Int): Int {
    throw UnsupportedOperationException()
  }

  override fun previous(): Int {
    throw UnsupportedOperationException()
  }

  override fun following(offset: Int): Int {
    throw UnsupportedOperationException()
  }

  override fun getText(): CharacterIterator? {
    throw UnsupportedOperationException()
  }

  override fun setText(newText: CharacterIterator?) {
    throw UnsupportedOperationException()
  }
}
