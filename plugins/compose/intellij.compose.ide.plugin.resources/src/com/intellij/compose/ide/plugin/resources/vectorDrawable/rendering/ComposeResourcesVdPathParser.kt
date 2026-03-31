/*
 * Copyright (C) 2015 The Android Open Source Project
 * Modified 2026 by JetBrains s.r.o.
 * Copyright (C) 2026 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.compose.ide.plugin.resources.vectorDrawable.rendering

import kotlin.math.abs

/**
 * Based on [com.android.ide.common.vectordrawable.PathParser].
 * Java source: https://cs.android.com/android-studio/platform/tools/base/+/05d6b9956098498ddf9b4538efcabf64a34b20f4:sdk-common/src/main/java/com/android/ide/common/vectordrawable/PathParser.java
 *
 * Key differences from Android original:
 * - Converted from Java class with static methods to Kotlin top-level functions
 * - Removed `ExtractFloatResult` inner class, using inline returns instead
 */
internal fun parsePathStringIntoNodes(value: String, mode: ParseMode?): Array<ComposeResourcesVdPath.Node> {
  val trimmed = value.trim()
  if (trimmed.isEmpty()) return emptyArray()

  val nodes = mutableListOf<ComposeResourcesVdPath.Node>()
  var start = 0
  var end = 1

  while (end < trimmed.length) {
    end = findNextCommandStart(trimmed, end)
    val segment = trimmed.substring(start, end)
    val cmd = segment[0]
    val values = getFloats(segment, mode)
    if (start == 0 && cmd != 'M' && cmd != 'm') {
      nodes.add(ComposeResourcesVdPath.Node('M', FloatArray(2)))
    }
    nodes.add(ComposeResourcesVdPath.Node(cmd, values))
    start = end++
  }

  if (end - start == 1 && start < trimmed.length) {
    nodes.add(ComposeResourcesVdPath.Node(trimmed[start], EMPTY_FLOAT_ARRAY))
  }
  return nodes.toTypedArray()
}

private fun findNextCommandStart(s: String, start: Int): Int {
  var i = start
  while (i < s.length) {
    val c = s[i]
    if ((c in 'A'..'Z' && c != 'E') || (c in 'a'..'z' && c != 'e')) return i
    i++
  }
  return i
}

enum class ParseMode { SVG, ANDROID }

private fun getFloats(s: String, mode: ParseMode?): FloatArray {
  if (s.isEmpty() || s[0].lowercaseChar() == 'z') return EMPTY_FLOAT_ARRAY

  val isArc = s[0].lowercaseChar() == 'a'
  val results = FloatArray(s.length)
  var index = 1
  var count = 0

  while (index < s.length) {
    val isSvgFlag = mode == ParseMode.SVG && isArc && (count % 7 == 3 || count % 7 == 4)
    val endPos = extractToken(s, index, isSvgFlag)
    val explicitSeparator = endPos < s.length && (s[endPos] == ' ' || s[endPos] == ',')

    if (index < endPos) {
      results[count++] = s.substring(index, endPos).toFloat()
    }

    index = if (explicitSeparator) endPos + 1 else endPos
  }
  if (isArc) adjustArcParams(results, count)
  return results.copyOf(count)
}

private fun extractToken(s: String, start: Int, isSvgFlag: Boolean): Int {
  var i = start
  var seenDot = false
  var prevWasExponent = false

  while (i < s.length) {
    val c = s[i]
    val isExponent = c == 'E' || c == 'e'
    when (c) {
      ' ', ',' -> return i
      '-' -> if (i != start && !prevWasExponent) return i
      '.' -> if (seenDot) return i else seenDot = true
    }
    prevWasExponent = isExponent
    i++
    if (isSvgFlag) return i
  }

  return i
}

private fun adjustArcParams(results: FloatArray, count: Int) {
  var i = 0
  while (i + 1 < count) {
    results[i] = abs(results[i])
    results[i + 1] = abs(results[i + 1])
    i += 7
  }
}

private val EMPTY_FLOAT_ARRAY = FloatArray(0)