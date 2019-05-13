/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.execution.configurations;

import junit.framework.TestCase;

public class CommandLineTokenizerTest extends TestCase {
  public void testBasic() {
    assertTokens("a b", "a", "b");
    assertTokens("\"a b\"", "a b");
    assertTokens("a\" b\"", "a b");
    assertTokens("\"a b", "a b");
    assertTokens("a b\"", "a", "b");
    assertTokens("a b\\\"", "a", "b\"");
    assertTokens("a b\" c", "a", "b c");
    assertTokens("\"a b\" c \"d e\"", "a b", "c", "d e");
    assertTokens("\"-Dquote=\\\\\"", "-Dquote=\\\\");
  }

  public void testEscape() {
    assertTokens("a\\ b", true, "a b");
    assertTokens("a\\ b", false, "a\\", "b");

    assertTokens("\"a\\ b\"", true, "a\\ b");
    assertTokens("\"a\\ b\"", false, "a\\ b");

    assertTokens("a\\ ", true, "a ");
    assertTokens("a\\ ", false, "a\\");

    assertTokens("\\\"", "\"");
    assertTokens("\"\\\" a \\\"\"", "\" a \"");
    assertTokens("\\\"a b\\\"", "\"a", "b\"");
    assertTokens("\\\"a\\ b\\\"", true, "\"a b\"");
    assertTokens("\\\"a\\ b\\\"", false, "\"a\\", "b\"");

    // Check tail \" as Java does (most compatible way to go: doubling the tail backslash), see comment in
    // http://cr.openjdk.java.net/~uta/openjdk-webrevs/JDK-8016046/webrev.01/src/windows/classes/java/lang/ProcessImpl.java.frames.html
    // line L188/R199
    assertTokens("\"-lib=c:\\My Lib\\\\\" \"-include=c:\\My Include\\\\\"", true, "-lib=c:\\My Lib\\\\", "-include=c:\\My Include\\\\");
  }

  private static void assertTokens(String cmd, String... tokens) {
    assertTokens(cmd, false, tokens);
  }

  private static void assertTokens(String cmd, boolean handleEscapedWhitespaces, String... tokens) {
    CommandLineTokenizer tokenizer = new CommandLineTokenizer(cmd, handleEscapedWhitespaces);

    assertEquals(tokens.length, tokenizer.countTokens());
    for (String token : tokens) {
      assertEquals(token, tokenizer.nextToken());
    }
  }
}
