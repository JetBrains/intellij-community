/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.intellij.util.ui.accessibility

import com.intellij.util.ui.accessibility.AccessibleContextUtil.combineAccessibleStrings
import com.intellij.util.ui.accessibility.AccessibleContextUtil.joinAccessibleStrings
import com.intellij.util.ui.accessibility.AccessibleContextUtil.replaceLineSeparatorsWithPunctuation
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class AccessibleContextUtilTest {
  @Test
  fun testReplaceLineSeparatorsWithPunctuation() {
    val p = AccessibleContextUtil.PUNCTUATION_CHARACTER
    val s = AccessibleContextUtil.PUNCTUATION_SEPARATOR

    Assertions.assertEquals("", replaceLineSeparatorsWithPunctuation(null))
    Assertions.assertEquals("", replaceLineSeparatorsWithPunctuation(""))
    Assertions.assertEquals("", replaceLineSeparatorsWithPunctuation("  "))

    Assertions.assertEquals("a" + p, replaceLineSeparatorsWithPunctuation(" a "))
    Assertions.assertEquals("a" + p, replaceLineSeparatorsWithPunctuation("a"))

    Assertions.assertEquals("a" + p + s + "b" + p, replaceLineSeparatorsWithPunctuation("a\nb"))
    Assertions.assertEquals("a" + p + s + "b" + p, replaceLineSeparatorsWithPunctuation("a.\nb"))
    Assertions.assertEquals("a" + p + s + "b" + p, replaceLineSeparatorsWithPunctuation("a.\nb."))

    Assertions.assertEquals("a" + p + s + "b" + p + s + "c" + p, replaceLineSeparatorsWithPunctuation("a\nb\n\nc"))
  }

  @Test
  fun testCombineAccessibleStrings() {
    Assertions.assertNull(combineAccessibleStrings(null, null))
    Assertions.assertNull(combineAccessibleStrings("", ""))
    Assertions.assertNull(combineAccessibleStrings(null, ""))
    Assertions.assertNull(combineAccessibleStrings("", null))

    Assertions.assertEquals("first", combineAccessibleStrings("first", null))
    Assertions.assertEquals("first", combineAccessibleStrings("first", ""))

    Assertions.assertEquals("second", combineAccessibleStrings(null, "second"))
    Assertions.assertEquals("second", combineAccessibleStrings("", "second"))

    Assertions.assertEquals("first second", combineAccessibleStrings("first", "second"))
    Assertions.assertEquals("first, second", combineAccessibleStrings("first", ", ", "second"))
  }

  @Test
  fun testJoinAccessibleStrings() {
    Assertions.assertEquals("first second third", joinAccessibleStrings(" ", "first", "second", "third"))

    Assertions.assertEquals("first second", joinAccessibleStrings(" ", "first", null, "second"))
    Assertions.assertEquals("first second", joinAccessibleStrings(" ", "first", "", "second"))

    Assertions.assertNull(joinAccessibleStrings(" "))
    Assertions.assertNull(joinAccessibleStrings(" ", null, null, null))
    Assertions.assertNull(joinAccessibleStrings(" ", "", "", ""))
    Assertions.assertNull(joinAccessibleStrings(" ", null, "", null))
  }
}
