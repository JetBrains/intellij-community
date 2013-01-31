/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.psi.impl.search;

import com.intellij.util.text.StringSearcher;
import junit.framework.TestCase;

/**
 * @author yole
 */
public class LowLevelSearchUtilTest extends TestCase {
  public void testBackslashBeforeSequence() {
    assertEquals(-1, doTest("n", "\\n"));
  }

  public void testEscapedBackslashBeforeSequence() {
    assertEquals(2, doTest("n", "\\\\n"));
  }

  public void testTwoBackslashesBeforeSequence() {
    assertEquals(-1, doTest("n", "\\\\\\n"));
  }

  public void testBackslashNBeforeSequence() {
    assertEquals(2, doTest("n", "\\nn"));
  }

  public void testBackslashBeforeSequenceNotBeginning() {
    assertEquals(-1, doTest("n", "%d\\n"));
  }

  private static int doTest(String pattern, String text) {
    StringSearcher searcher = new StringSearcher(pattern, true, true, true);
    return LowLevelSearchUtil.searchWord(text, 0, text.length(), searcher, null);
  }
}
