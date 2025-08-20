// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.text

import fleet.util.multiplatform.Actual

/**
 * [com.intellij.util.text.fromSequenceWithoutCopyingPlatformSpecific]
 */
@Actual("fromSequenceWithoutCopyingPlatformSpecific")
internal fun fromSequenceWithoutCopyingPlatformSpecificJs(seq: CharSequence?): CharArray? {
  return null
}

/**
 * [com.intellij.util.text.getCharsPlatformSpecific]
 */
@Actual("getCharsPlatformSpecific")
internal fun getCharsPlatformSpecificJs(string: CharSequence, srcOffset: Int, dst: CharArray, dstOffset: Int, len: Int): Boolean {
  return false
}