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
import org.junit.Assert
import org.junit.Test

class AccessibleContextUtilTest {
  @Test
  fun testReplaceLineSeparatorsWithPunctuation() {
    val p = AccessibleContextUtil.PUNCTUATION_CHARACTER
    val s = AccessibleContextUtil.PUNCTUATION_SEPARATOR

    Assert.assertEquals("", replaceLineSeparatorsWithPunctuation(null))
    Assert.assertEquals("", replaceLineSeparatorsWithPunctuation(""))
    Assert.assertEquals("", replaceLineSeparatorsWithPunctuation("  "))

    Assert.assertEquals("a" + p, replaceLineSeparatorsWithPunctuation(" a "))
    Assert.assertEquals("a" + p, replaceLineSeparatorsWithPunctuation("a"))

    Assert.assertEquals("a" + p + s + "b" + p, replaceLineSeparatorsWithPunctuation("a\nb"))
    Assert.assertEquals("a" + p + s + "b" + p, replaceLineSeparatorsWithPunctuation("a.\nb"))
    Assert.assertEquals("a" + p + s + "b" + p, replaceLineSeparatorsWithPunctuation("a.\nb."))

    Assert.assertEquals("a" + p + s + "b" + p + s + "c" + p, replaceLineSeparatorsWithPunctuation("a\nb\n\nc"))
  }

  @Test
  fun testCombineAccessibleStrings() {
    Assert.assertNull(combineAccessibleStrings(null, null))
    Assert.assertNull(combineAccessibleStrings("", ""))
    Assert.assertNull(combineAccessibleStrings(null, ""))
    Assert.assertNull(combineAccessibleStrings("", null))

    Assert.assertEquals("first", combineAccessibleStrings("first", null))
    Assert.assertEquals("first", combineAccessibleStrings("first", ""))

    Assert.assertEquals("second", combineAccessibleStrings(null, "second"))
    Assert.assertEquals("second", combineAccessibleStrings("", "second"))

    Assert.assertEquals("first second", combineAccessibleStrings("first", "second"))
    Assert.assertEquals("first, second", combineAccessibleStrings("first", ", ", "second"))
  }

  @Test
  fun testJoinAccessibleStrings() {
    Assert.assertEquals("first second third", joinAccessibleStrings(" ", "first", "second", "third"))

    Assert.assertEquals("first second", joinAccessibleStrings(" ", "first", null, "second"))
    Assert.assertEquals("first second", joinAccessibleStrings(" ", "first", "", "second"))

    Assert.assertNull(joinAccessibleStrings(" "))
    Assert.assertNull(joinAccessibleStrings(" ", null, null, null))
    Assert.assertNull(joinAccessibleStrings(" ", "", "", ""))
    Assert.assertNull(joinAccessibleStrings(" ", null, "", null))
  }
}
