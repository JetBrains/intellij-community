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
package com.intellij.util.ui.accessibility;

import org.junit.Assert;
import org.junit.Test;

public class AccessibleContextUtilTest {
  @Test
  public void testReplaceLineSeparatorsWithPunctuation() {
    final String p = AccessibleContextUtil.PUNCTUATION_CHARACTER;
    final String s = AccessibleContextUtil.PUNCTUATION_SEPARATOR;

    Assert.assertEquals("", AccessibleContextUtil.replaceLineSeparatorsWithPunctuation(null));
    Assert.assertEquals("", AccessibleContextUtil.replaceLineSeparatorsWithPunctuation(""));
    Assert.assertEquals("", AccessibleContextUtil.replaceLineSeparatorsWithPunctuation("  "));

    Assert.assertEquals("a" + p, AccessibleContextUtil.replaceLineSeparatorsWithPunctuation(" a "));
    Assert.assertEquals("a" + p, AccessibleContextUtil.replaceLineSeparatorsWithPunctuation("a"));

    Assert.assertEquals("a" + p + s + "b" + p, AccessibleContextUtil.replaceLineSeparatorsWithPunctuation("a\nb"));
    Assert.assertEquals("a" + p + s + "b" + p, AccessibleContextUtil.replaceLineSeparatorsWithPunctuation("a.\nb"));
    Assert.assertEquals("a" + p + s + "b" + p, AccessibleContextUtil.replaceLineSeparatorsWithPunctuation("a.\nb."));

    Assert.assertEquals("a" + p + s + "b" + p + s + "c" + p, AccessibleContextUtil.replaceLineSeparatorsWithPunctuation("a\nb\n\nc"));
  }

  @Test
  public void testCombineAccessibleStrings() {
    Assert.assertNull(AccessibleContextUtil.combineAccessibleStrings(null, null));
    Assert.assertNull(AccessibleContextUtil.combineAccessibleStrings("", ""));
    Assert.assertNull(AccessibleContextUtil.combineAccessibleStrings(null, ""));
    Assert.assertNull(AccessibleContextUtil.combineAccessibleStrings("", null));

    Assert.assertEquals("first", AccessibleContextUtil.combineAccessibleStrings("first", null));
    Assert.assertEquals("first", AccessibleContextUtil.combineAccessibleStrings("first", ""));

    Assert.assertEquals("second", AccessibleContextUtil.combineAccessibleStrings(null, "second"));
    Assert.assertEquals("second", AccessibleContextUtil.combineAccessibleStrings("", "second"));

    Assert.assertEquals("first second", AccessibleContextUtil.combineAccessibleStrings("first", "second"));
    Assert.assertEquals("first, second", AccessibleContextUtil.combineAccessibleStrings("first", ", ", "second"));
  }
}
