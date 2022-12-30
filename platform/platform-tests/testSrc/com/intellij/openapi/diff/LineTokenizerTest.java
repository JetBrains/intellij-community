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

import junit.framework.TestCase;

import static org.assertj.core.api.Assertions.assertThat;

public class LineTokenizerTest extends TestCase {
  public void test() {
    assertThat(new LineTokenizer("a\nb\n\rc\rd\r\n").execute()).containsExactly("a\n", "b\n", "c\n", "d\n");
    assertThat(new LineTokenizer("a\nb").execute()).containsExactly("a\n", "b");
    LineTokenizer lineTokenizer = new LineTokenizer("a\n\r\r\nb");
    assertThat(lineTokenizer.execute()).containsExactly("a\n", "\n", "b");
    assertEquals("\n\r", lineTokenizer.getLineSeparator());
  }
}
