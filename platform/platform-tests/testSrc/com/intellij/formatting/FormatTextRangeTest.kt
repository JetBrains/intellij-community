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

class FormatTextRangeTest {
  
  @Test
  fun `without heading space`() {
    //equal (startOffset, endOffset)
    val ranges = newFormatRanges(5, 6, false)
    assertThat(ranges.isWhitespaceReadOnly(TextRange(4, 5))).isTrue()
    assertThat(ranges.isWhitespaceReadOnly(TextRange(6, 7))).isTrue()
  }
  
  @Test
  fun `with heading space`() {
    //equal [startOffset, endOffset)
    val ranges = newFormatRanges(5, 6, true)
    assertThat(ranges.isWhitespaceReadOnly(TextRange(4, 5))).isFalse()
    assertThat(ranges.isWhitespaceReadOnly(TextRange(6, 7))).isTrue()
  }
  
  @Test
  fun `check processHeadingSpace difference at start offset`() {
    val noHeadingSpace = newFormatRanges(5, 6, false)
    assertThat(noHeadingSpace.isWhitespaceReadOnly(TextRange(0, 5))).isTrue()

    val withHeadingSpace = newFormatRanges(5, 6, true)
    assertThat(withHeadingSpace.isWhitespaceReadOnly(TextRange(0, 5))).isFalse()
  }
  
  @Test
  fun `check processHeadingSpace difference at end offset`() {
    val noHeadingSpace = newFormatRanges(5, 6, false)
    assertThat(noHeadingSpace.isWhitespaceReadOnly(TextRange(6, 7))).isTrue()

    val withHeadingSpace = newFormatRanges(5, 6, true)
    assertThat(withHeadingSpace.isWhitespaceReadOnly(TextRange(6, 7))).isTrue()
  }
  
  @Test
  fun `check isWhitespaceReadOnly no heading space`() {
    val ranges = newFormatRanges(10, 30, false)
    
    assertThat(ranges.isWhitespaceReadOnly(TextRange(0, 10))).isTrue()
    assertThat(ranges.isWhitespaceReadOnly(TextRange(0, 11))).isFalse()
    
    assertThat(ranges.isWhitespaceReadOnly(TextRange(29, 30))).isFalse()
    assertThat(ranges.isWhitespaceReadOnly(TextRange(30, 40))).isTrue()
  }

  @Test
  fun `check isWhitespaceReadOnly with heading space`() {
    val ranges = newFormatRanges(10, 30, true)

    assertThat(ranges.isWhitespaceReadOnly(TextRange(0, 9))).isTrue()
    assertThat(ranges.isWhitespaceReadOnly(TextRange(31, 40))).isTrue()

    assertThat(ranges.isWhitespaceReadOnly(TextRange(9, 10))).isFalse()
    assertThat(ranges.isWhitespaceReadOnly(TextRange(30, 31))).isTrue()
  }

  @Test
  fun `check partial intersection no heading space`() {
    val ranges = newFormatRanges(10, 30, false)
    assertThat(ranges.isWhitespaceReadOnly(TextRange(9, 10))).isTrue()
    assertThat(ranges.isWhitespaceReadOnly(TextRange(9, 11))).isFalse()
    
    assertThat(ranges.isWhitespaceReadOnly(TextRange(29, 31))).isFalse()
    assertThat(ranges.isWhitespaceReadOnly(TextRange(31, 32))).isTrue()
  }

  @Test
  fun `check partial intersection with heading space`() {
    val ranges = newFormatRanges(10, 30, true)
    assertThat(ranges.isWhitespaceReadOnly(TextRange(8, 9))).isTrue()
    assertThat(ranges.isWhitespaceReadOnly(TextRange(9, 10))).isFalse()
    
    assertThat(ranges.isWhitespaceReadOnly(TextRange(29, 31))).isFalse()
    assertThat(ranges.isWhitespaceReadOnly(TextRange(31, 32))).isTrue()
  }
  
  @Test
  fun `check contains no heading space`() {
    val ranges = newFormatRanges(10, 30, false)
    assertThat(ranges.isWhitespaceReadOnly(TextRange(15, 20))).isFalse()
    assertThat(ranges.isWhitespaceReadOnly(TextRange(11, 29))).isFalse()
    assertThat(ranges.isWhitespaceReadOnly(TextRange(5, 35))).isFalse()
  }
  
  private fun newFormatRanges(start: Int, end: Int, processHeadingSpace: Boolean = false): FormatTextRanges {
    return FormatTextRanges(TextRange(start, end), processHeadingSpace)
  }

}