// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.parser;

import com.intellij.lexer.Lexer;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.testFramework.LexerTestCase;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyLexer;
import org.jetbrains.plugins.groovy.util.TestUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class GroovyLexerTest extends LexerTestCase {
  @NotNull
  @Override
  protected Lexer createLexer() {
    return new GroovyLexer();
  }

  @NotNull
  @Override
  protected String getDirPath() {
    return TestUtils.getTestDataPath() + "lexer";
  }

  @Override
  protected void doTest(@NotNull @NonNls String text) {
    super.doTest(text);
    checkCorrectRestart(text);
  }

  @Override
  protected String printTokens(@NotNull final Lexer lexer, @NotNull CharSequence text, int start) {
    lexer.start(text, start, text.length());
    List<List<String>> tokens = new ArrayList<>(Arrays.asList(new ArrayList<>(Arrays.asList("offset", "state", "text", "type"))));
    Object tokenType;
    while ((tokenType = lexer.getTokenType()) != null) {
      tokens.add(List.of(String.valueOf(lexer.getTokenStart()), String.valueOf(lexer.getState()),
                         "'" + StringUtil.escapeLineBreak(lexer.getTokenText()) + "'", tokenType.toString()));
      lexer.advance();
    }

    return formatTable(tokens);
  }

  private static String formatTable(List<List<String>> tokens) {
    int[] max = new int[tokens.get(0).size()];
    for (List<String> token : tokens) {
      for (int i = 0; i < token.size(); i++) {
        String column = token.get(i);
        max[i] = Math.max(column.length(), max[i]);
      }
    }

    StringBuilder result = new StringBuilder();
    for (List<String> token : tokens) {
      for (int i = 0; i < token.size(); i++) {
        String column = token.get(i);
        int padding = Math.max(column.length(), max[i]) - column.length() + 1;
        result.append(column).append(" ".repeat(padding));
      }
      result.append("\n");
    }

    return result.toString();
  }

  public void testComments() {
    doTest("""
             /**/
             /***/
             //
             //
             
             //
             
             
             //
             """);
  }
}
