/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

/*
 * @author Eugene Zhuravlev
 */
package com.intellij.util.text;

import junit.framework.TestCase;

public class StringSearcherTest extends TestCase {
  public void testSearchPatternAtTheEnd() {
    final String pattern = "bc";
    final String text = "aabc";

    StringSearcher searcher = new StringSearcher(pattern, true, true);
    final int index = searcher.scan(text);

    assertEquals(text.indexOf("bc"), index);
  }

  public void testCaseInsensitiveWithUnicode() {
    String pattern = "SİL";
    final String text = "SİL SIL";
    final int firstPos = 0;
    final int secondPos = text.indexOf("SIL");

    StringSearcher searcher = new StringSearcher(pattern, false, true);
    int index = searcher.scan(text);

    assertEquals(firstPos, index);
    assertEquals(secondPos, searcher.scan(text, index + 1, text.length()));
    pattern = "sil";

    searcher = new StringSearcher(pattern, false, true);
    index = searcher.scan(text);

    assertEquals(firstPos, index);
    assertEquals(secondPos, searcher.scan(text, index + 1, text.length()));

    searcher = new StringSearcher(pattern, false, false);
    index = searcher.scan(text, 0, text.length());

    assertEquals(secondPos, index);
    assertEquals(firstPos, searcher.scan(text, 0, index - 1));
  }

  public void testSearchBackwardLastOne() {
    final String pattern = "c";
    final String text = "aabc";
    final int pos = text.lastIndexOf(pattern);
    final StringSearcher searcher = new StringSearcher(pattern, true, false);
    final int index = searcher.scan(text);

    assertEquals(pos, index);
  }

  public void testOneElementPatternAndBackward() {
    assertEquals(-1, new StringSearcher("1", false, false).scan("SIL"));
    assertEquals(0, new StringSearcher("S", false, false).scan("SIL"));
    assertEquals(2, new StringSearcher("L", false, false).scan("SIL"));
    assertEquals(0, new StringSearcher("SI", false, false).scan("SIL"));
    assertEquals(-1, new StringSearcher("SL", false, false).scan("SIL"));
    assertEquals(1, new StringSearcher("IL", false, false).scan("SIL"));
    assertEquals(0, new StringSearcher("SIL", false, false).scan("SIL"));
    assertEquals(-1, new StringSearcher("0SIL", false, false).scan("SIL"));
  }
}
