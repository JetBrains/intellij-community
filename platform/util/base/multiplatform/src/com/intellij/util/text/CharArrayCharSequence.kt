// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.text

import com.intellij.openapi.util.text.CharSequenceWithStringHash
import com.intellij.openapi.util.text.stringHashCode
import com.intellij.util.text.CharArrayUtilKmp.regionMatches
import kotlin.math.min
import kotlin.jvm.JvmField
import kotlin.jvm.Transient

open class CharArrayCharSequence(
  @JvmField protected val myChars: CharArray,
  @JvmField protected val myStart: Int,
  @JvmField protected val myEnd: Int,
) : CharSequenceBackedByArray, CharSequenceWithStringHash {

  constructor(vararg chars: Char) : this(chars, 0, chars.size)

  @Transient
  private var hash = 0

  init {
    if (myStart < 0 || myEnd > myChars.size || myStart > myEnd) {
      throw IndexOutOfBoundsException("chars.length:" + myChars.size + ", start:" + myStart + ", end:" + myEnd)
    }
  }

  override val length: Int
    get() {
      return myEnd - myStart
    }

  override fun get(index: Int): Char {
    return myChars[index + myStart]
  }

  override fun subSequence(start: Int, end: Int): CharSequence {
    return if (start == 0 && end == length) this else CharArrayCharSequence(myChars, myStart + start, myStart + end)
  }

  override fun toString(): String {
    return myChars.concatToString(myStart, myEnd) //TODO StringFactory
  }

  override val chars: CharArray
    get() {
      if (myStart == 0) return myChars
      val chars = CharArray(length)
      getChars(chars, 0)
      return chars
    }

  override fun getChars(dst: CharArray, dstOffset: Int) {
    myChars.copyInto(dst, dstOffset, myStart, myEnd)
  }

  override fun equals(anObject: Any?): Boolean {
    if (this === anObject) {
      return true
    }
    if (anObject == null || this::class != anObject::class || length != (anObject as CharSequence).length) {
      return false
    }
    return myChars.regionMatches(myStart, myEnd, anObject)
  }

  /**
   * See [java.io.Reader.read];
   */
  fun readCharsTo(start: Int, cbuf: CharArray, off: Int, len: Int): Int {
    val readChars = min(len, length - start)
    if (readChars <= 0) return -1

    myChars.copyInto(cbuf, off, myStart + start, myStart + start + readChars)
    return readChars
  }

  override fun hashCode(): Int {
    var h = hash
    if (h == 0) {
      h = myChars.stringHashCode(myStart, myEnd)
      hash = h
    }
    return h
  }
}
