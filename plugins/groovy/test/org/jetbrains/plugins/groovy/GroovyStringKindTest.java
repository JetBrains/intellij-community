// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy;

import org.jetbrains.plugins.groovy.lang.psi.util.StringKind;
import org.jetbrains.plugins.groovy.util.GroovyLatestTest;
import org.jetbrains.plugins.groovy.util.TestUtils;
import org.junit.Test;

import java.util.Map;

import static org.jetbrains.plugins.groovy.lang.psi.util.StringKind.TestsOnly.*;
import static org.junit.Assert.assertEquals;

public class GroovyStringKindTest extends GroovyLatestTest {

  private static void doEscapeTests(StringKind kind, Map<String, String> data) {
    TestUtils.runAll(data.entrySet(), entry -> {
      String unescaped = entry.getKey();
      String expectedEscaped = entry.getValue();
      assertEquals(expectedEscaped, kind.escape(unescaped));
    });
  }

  @Test
  public void escapeSingleQuoted() {
    doEscapeTests(SINGLE_QUOTED, Map.ofEntries(
      Map.entry("\n", "\\n"),
      Map.entry("\r", "\\r"),
      Map.entry("\b", "\\b"),
      Map.entry("\t", "\\t"),
      Map.entry("\f", "\\f"),
      Map.entry("a\\b", "a\\\\b"),
      Map.entry("5/6", "5/6"),
      Map.entry("$", "$"),
      Map.entry("'", "\\'"),
      Map.entry("\"", "\"")
    ));
  }

  @Test
  public void escapeTripleSingleQuoted() {
    doEscapeTests(TRIPLE_SINGLE_QUOTED, Map.ofEntries(
      Map.entry("\n", "\n"),
      Map.entry("\b", "\\b"),
      Map.entry("\t", "\\t"),
      Map.entry("\f", "\\f"),
      Map.entry("a\\b", "a\\\\b"),
      Map.entry("5/6", "5/6"),
      Map.entry("$", "$"),
      Map.entry("\"", "\""),
      Map.entry("\"\"", "\"\""),
      Map.entry("\"\"\"", "\"\"\""),
      Map.entry("\"\"\"\"", "\"\"\"\""),
      Map.entry("'", "\\'"),
      Map.entry("'a", "'a"),
      Map.entry("''a", "''a")
    ));
  }

  @Test
  public void escapeDoubleQuoted() {
    doEscapeTests(DOUBLE_QUOTED, Map.ofEntries(
      Map.entry("\n", "\\n"),
      Map.entry("\r", "\\r"),
      Map.entry("\b", "\\b"),
      Map.entry("\t", "\\t"),
      Map.entry("\f", "\\f"),
      Map.entry("a\\b", "a\\\\b"),
      Map.entry("5/6", "5/6"),
      Map.entry("$", "\\$"),
      Map.entry("\\$", "\\\\\\$"),
      Map.entry("'", "'"),
      Map.entry("\"", "\\\"")
    ));
  }

  @Test
  public void escapeTripleDoubleQuoted() {
    doEscapeTests(TRIPLE_DOUBLE_QUOTED, Map.ofEntries(
      Map.entry("\n", "\n"),
      Map.entry("\b", "\\b"),
      Map.entry("\t", "\\t"),
      Map.entry("\f", "\\f"),
      Map.entry("a\\b", "a\\\\b"),
      Map.entry("5/6", "5/6"),
      Map.entry("$", "\\$"),
      Map.entry("\\$", "\\\\\\$"),
      Map.entry("'", "'"),
      Map.entry("''", "''"),
      Map.entry("'''", "'''"),
      Map.entry("''''", "''''"),
      Map.entry("\"a", "\"a"),
      Map.entry("\"\"a", "\"\"a")
    ));
  }

  @Test
  public void escapeSlashy() {
    doEscapeTests(SLASHY, Map.ofEntries(
      Map.entry("\n", "\n"),
      Map.entry("\r", "\\u000D"),
      Map.entry("\b", "\\u0008"),
      Map.entry("\t", "\\u0009"),
      Map.entry("\f", "\\u000C"),
      Map.entry("a\\b", "a\\b"),
      Map.entry("5\\/6", "5\\\\/6"),
      Map.entry("$", "\\u0024"),
      Map.entry("'", "'"),
      Map.entry("\"", "\"")
    ));
  }

  @Test
  public void escapeDollarSlashy() {
    doEscapeTests(DOLLAR_SLASHY, Map.ofEntries(
      Map.entry("\n", "\n"),
      Map.entry("\r", "\\u000D"),
      Map.entry("\b", "\\u0008"),
      Map.entry("\t", "\\u0009"),
      Map.entry("\f", "\\u000C"),
      Map.entry("a\\b", "a\\b"),
      Map.entry("/5/6/", "/5/6/"),
      Map.entry("/'/", "/'/"),
      Map.entry("/\"/", "/\"/"),
      Map.entry("'$_'", "'$$_'"),
      Map.entry("'hello $ world'", "'hello $ world'"),
      Map.entry("'hello / world'", "'hello / world'"),
      Map.entry("'hello $/ world'", "'hello $$/ world'"),
      Map.entry("'hello $$ world'", "'hello $$$ world'"),
      Map.entry("'hello /$ world'", "'hello $/$ world'")
    ));
  }
}
