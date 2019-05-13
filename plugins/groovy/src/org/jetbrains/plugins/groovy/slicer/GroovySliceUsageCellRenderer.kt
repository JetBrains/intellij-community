/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.plugins.groovy.slicer

import com.intellij.slicer.SliceUsage
import com.intellij.slicer.SliceUsageCellRendererBase
import com.intellij.util.FontUtil

class GroovySliceUsageCellRenderer : SliceUsageCellRendererBase() {
  override fun customizeCellRendererFor(sliceUsage: SliceUsage) {
    if (sliceUsage !is GroovySliceUsage) return

    for ((i, textChunk) in sliceUsage.getText().withIndex()) {
      append(textChunk.text, textChunk.simpleAttributesIgnoreBackground)
      if (i == 0) {
        append(FontUtil.spaceAndThinSpace())
      }
    }
  }
}
