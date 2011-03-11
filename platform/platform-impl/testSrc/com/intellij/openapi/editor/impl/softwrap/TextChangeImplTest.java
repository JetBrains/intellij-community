/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.openapi.editor.impl.softwrap;

import com.intellij.openapi.editor.TextChange;
import com.intellij.openapi.editor.impl.TextChangeImpl;
import com.intellij.openapi.util.text.StringUtil;
import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * @author Denis Zhdanov
 * @since 07/07/2010
 */
public class TextChangeImplTest {

  @Test(expected = IllegalArgumentException.class)
  public void negativeStartIndex() {
    new TextChangeImpl("", -1);
  }

  @Test(expected = IllegalArgumentException.class)
  public void negativeEndIndex() {
    new TextChangeImpl("", 0, -1);
  }

  @Test(expected = IllegalArgumentException.class)
  public void inconsistentIndices() {
    new TextChangeImpl("", 2, 1);
  }

  @Test
  public void propertiesExposing() {
    int start = 1;
    int end = 10;
    String text = "test";
    TextChange textChange = new TextChangeImpl(text, start, end);
    assertTrue(StringUtil.equals(text, textChange.getText()));
    assertArrayEquals(text.toCharArray(), textChange.getChars());
    assertEquals(start, textChange.getStart());
    assertEquals(end, textChange.getEnd());
  }

  @Test(expected = IllegalArgumentException.class)
  public void advanceToNegativeOffset() {
    new TextChangeImpl("", 1, 2).advance(-2);
  }

  @Test
  public void advance() {
    TextChangeImpl base = new TextChangeImpl("xyz", 3, 5);

    int[] offsets = {5, 0, -3};
    for (int offset : offsets) {
      int start = base.getStart();
      int end = base.getEnd();
      base.advance(offset);
      assertEquals(new TextChangeImpl(base.getText(), start + offset, end + offset), base);
    }
  }
}
