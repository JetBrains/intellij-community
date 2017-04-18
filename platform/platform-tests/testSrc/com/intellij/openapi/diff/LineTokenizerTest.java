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
package com.intellij.openapi.diff;

import com.intellij.util.Assertion;
import junit.framework.TestCase;

public class LineTokenizerTest extends TestCase {
  private final Assertion CHECK = new Assertion();

  public void test() {
    CHECK.compareAll(new String[]{"a\n", "b\n", "c\n", "d\n"}, new LineTokenizer("a\nb\n\rc\rd\r\n").execute());
    CHECK.compareAll(new String[]{"a\n", "b"}, new LineTokenizer("a\nb").execute());
    LineTokenizer lineTokenizer = new LineTokenizer("a\n\r\r\nb");
    CHECK.compareAll(new String[]{"a\n", "\n", "b"}, lineTokenizer.execute());
    assertEquals("\n\r", lineTokenizer.getLineSeparator());
  }
}
