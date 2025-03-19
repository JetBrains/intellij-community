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
import org.jetbrains.annotations.NotNull;

import static org.assertj.core.api.Assertions.assertThat;

public class LineTokenizerTest extends TestCase {
  public void test() {
    LineTokenizer lineTokenizer = new LineTokenizer("a\n\r\r\nb");
    lineTokenizer.execute();
    assertEquals("\n\r", lineTokenizer.getLineSeparator());

    assertTokensAre("a\n\r\r\nb",
                    "a\n", "\n", "b");
    assertTokensAre("a\nb\n\rc\rd\r\n",
                    "a\n", "b\n", "c\n", "d\n");
    assertTokensAre("a\nb",
                    "a\n", "b");

    assertTokensAre("");
    assertTokensAre(" ",
                    " ");
    assertTokensAre("\n",
                    "\n");
    assertTokensAre("\r",
                    "\n");
    assertTokensAre("a\n\n\n",
                    "a\n", "\n", "\n");
    assertTokensAre("a\r\r\r",
                    "a\n", "\n", "\n");
  }

  private static void assertTokensAre(@NotNull String input, String... expected) {
    String[] actual = new LineTokenizer(input).execute();
    assertThat(actual).containsExactly(expected);
  }
}
