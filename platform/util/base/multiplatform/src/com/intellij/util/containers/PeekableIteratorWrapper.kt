// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.containers

/**
 * Consider using [com.google.common.collect.Iterators.peekingIterator] instead.
 */
open class PeekableIteratorWrapper<T>(private val myIterator: Iterator<T>) : PeekableIterator<T> {
  private var myValue: T? = null
  private var myValidValue = false

  init {
    advance()
  }

  override fun hasNext(): Boolean {
    return myValidValue
  }

  override fun next(): T {
    if (myValidValue) {
      val save = myValue!!
      advance()
      return save
    }
    throw NoSuchElementException()
  }

  override fun peek(): T {
    if (myValidValue) {
      return myValue!!
    }
    throw NoSuchElementException()
  }

  private fun advance() {
    myValidValue = myIterator.hasNext()
    myValue = if (myValidValue) myIterator.next() else null
  }
}
