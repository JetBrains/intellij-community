/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.formatting

import com.intellij.openapi.util.TextRange
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

class FormatTextRangesTest {
  
  @Test
  fun `empty format range present`() {
    val ranges = FormatTextRanges(TextRange(10, 30), false)
    ranges.add(TextRange(31, 31), true)
    assertThat(ranges.isWhitespaceReadOnly(TextRange(25, 35))).isFalse()
  }
  
}


class FormatRangesStorageTest {

  private fun FormatTextRange.assertRange(start: Int, end: Int, isProcessHeadingSpace: Boolean) {
    assertThat(textRange).isEqualTo(TextRange(start, end))
    assertThat(isProcessHeadingWhitespace).isEqualTo(isProcessHeadingSpace)
  }

  @Test
  fun `check fully contained text range is not added`() {
    val storage = FormatRangesStorage()

    storage.add(TextRange(10, 20), false)
    storage.add(TextRange(15, 18), false)
    val ranges = storage.getRanges()

    assertThat(ranges).hasSize(1)
    ranges[0].assertRange(10, 20, false)
  }

  @Test
  fun `check left boundary is not formatted`() {
    val storage = FormatRangesStorage()

    storage.add(TextRange(10, 20), false)
    storage.add(TextRange(5, 10), false)
    val ranges = storage.getRanges()
    assertThat(ranges).hasSize(2)
  }


  @Test
  fun `check left boundary is formatted`() {
    val storage = FormatRangesStorage()

    storage.add(TextRange(10, 20), true)
    storage.add(TextRange(5, 10), false)
    val ranges = storage.getRanges()
    
    assertThat(ranges).hasSize(1)
    ranges[0].assertRange(5, 20, false)
  }

  @Test
  fun `check right boundary is not formatted`() {
    val storage = FormatRangesStorage()

    storage.add(TextRange(10, 20), false)
    storage.add(TextRange(20, 30), false)
    val ranges = storage.getRanges()
    assertThat(ranges).hasSize(2)
  }


  @Test
  fun `check right boundary is formatted`() {
    val storage = FormatRangesStorage()

    storage.add(TextRange(10, 20), false)
    storage.add(TextRange(19, 30), false)
    val ranges = storage.getRanges()
    assertThat(ranges).hasSize(1)
    ranges[0].assertRange(10, 30, false)
  }


  @Test
  fun `ensure isProcessHeading space state merged`() {
    val storage = FormatRangesStorage()
    
    storage.add(TextRange(10, 20), false)
    storage.add(TextRange(10, 20), true)
    val ranges = storage.getRanges()
    assertThat(ranges).hasSize(1)
    ranges[0].assertRange(10, 20, true)
  }
  


}