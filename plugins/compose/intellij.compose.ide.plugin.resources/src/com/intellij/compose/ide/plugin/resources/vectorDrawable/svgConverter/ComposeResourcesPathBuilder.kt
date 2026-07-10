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
package com.intellij.compose.ide.plugin.resources.vectorDrawable.svgConverter

import com.intellij.compose.ide.plugin.resources.vectorDrawable.svgConverter.ComposeResourcesSvgTree.Companion.formatFloatValue

/**
 * Based on [com.android.ide.common.vectordrawable.PathBuilder]
 * Java source: https://cs.android.com/android-studio/platform/tools/base/+/74a5c9785c3ad9f7f0ce8fbf4adfb2ae2c6bec2c:sdk-common/src/main/java/com/android/ide/common/vectordrawable/PathBuilder.java
 *
 * Key differences from Android original:
 * - Removed unused methods: absolute vertical/horizontal, cubic/quadratic curves, smooth curves, absoluteClose, isEmpty
 */
internal class ComposeResourcesPathBuilder {

  private val pathData = StringBuilder()

  fun absoluteMoveTo(x: Double, y: Double): ComposeResourcesPathBuilder {
    pathData
      .append('M')
      .append(formatFloatValue(x))
      .append(',')
      .append(formatFloatValue(y))
    return this
  }

  fun relativeMoveTo(x: Double, y: Double): ComposeResourcesPathBuilder {
    pathData
      .append('m')
      .append(formatFloatValue(x))
      .append(',')
      .append(formatFloatValue(y))
    return this
  }

  fun absoluteLineTo(x: Double, y: Double): ComposeResourcesPathBuilder {
    pathData
      .append('L')
      .append(formatFloatValue(x))
      .append(',')
      .append(formatFloatValue(y))
    return this
  }

  fun relativeLineTo(x: Double, y: Double): ComposeResourcesPathBuilder {
    pathData
      .append('l')
      .append(formatFloatValue(x))
      .append(',')
      .append(formatFloatValue(y))
    return this
  }

  fun relativeVerticalTo(v: Double): ComposeResourcesPathBuilder {
    pathData.append('v').append(formatFloatValue(v))
    return this
  }

  fun relativeHorizontalTo(h: Double): ComposeResourcesPathBuilder {
    pathData.append('h').append(formatFloatValue(h))
    return this
  }

  fun absoluteArcTo(
    rx: Double,
    ry: Double,
    rotation: Boolean,
    largeArc: Boolean,
    sweep: Boolean,
    x: Double,
    y: Double,
  ): ComposeResourcesPathBuilder {
    pathData
      .append('A')
      .append(formatFloatValue(rx))
      .append(',')
      .append(formatFloatValue(ry))
      .append(',')
      .append(encodeBoolean(rotation))
      .append(',')
      .append(encodeBoolean(largeArc))
      .append(',')
      .append(encodeBoolean(sweep))
      .append(',')
      .append(formatFloatValue(x))
      .append(',')
      .append(formatFloatValue(y))
    return this
  }

  fun relativeArcTo(
    rx: Double,
    ry: Double,
    rotation: Boolean,
    largeArc: Boolean,
    sweep: Boolean,
    x: Double,
    y: Double,
  ): ComposeResourcesPathBuilder {
    pathData
      .append('a')
      .append(formatFloatValue(rx))
      .append(',')
      .append(formatFloatValue(ry))
      .append(',')
      .append(encodeBoolean(rotation))
      .append(',')
      .append(encodeBoolean(largeArc))
      .append(',')
      .append(encodeBoolean(sweep))
      .append(',')
      .append(formatFloatValue(x))
      .append(',')
      .append(formatFloatValue(y))
    return this
  }

  fun relativeClose(): ComposeResourcesPathBuilder {
    pathData.append('z')
    return this
  }

  override fun toString(): String =
    pathData.toString()

  private fun encodeBoolean(flag: Boolean): Char =
    if (flag) '1' else '0'
}
