// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
    // http://cr.openjdk.org/~uta/openjdk-webrevs/JDK-8016046/webrev.01/src/windows/classes/java/lang/ProcessImpl.java.frames.html
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
