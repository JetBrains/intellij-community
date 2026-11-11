// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy;

import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.util.StringKind;
import org.jetbrains.plugins.groovy.util.TestUtils;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static com.intellij.testFramework.EdtTestUtil.runInEdtAndWait;
import static org.jetbrains.plugins.groovy.lang.psi.util.StringKind.TestsOnly.DOLLAR_SLASHY;
import static org.jetbrains.plugins.groovy.lang.psi.util.StringKind.TestsOnly.DOUBLE_QUOTED;
import static org.jetbrains.plugins.groovy.lang.psi.util.StringKind.TestsOnly.SINGLE_QUOTED;
import static org.jetbrains.plugins.groovy.lang.psi.util.StringKind.TestsOnly.SLASHY;
import static org.jetbrains.plugins.groovy.lang.psi.util.StringKind.TestsOnly.TRIPLE_DOUBLE_QUOTED;
import static org.jetbrains.plugins.groovy.lang.psi.util.StringKind.TestsOnly.TRIPLE_SINGLE_QUOTED;
import static org.jetbrains.plugins.groovy.util.GroovyFixture.groovyFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;

@TestApplication
public class GroovyStringKindTest {
  private final TestFixture<JavaCodeInsightTestFixture> groovy = groovyFixture(GroovyProjectDescriptors.GROOVY_2_5);

  public @NotNull JavaCodeInsightTestFixture getFixture() {
    return groovy.get();
  }

  private static void doEscapeTests(StringKind kind, Map<String, String> data) {
    TestUtils.runAll(data.entrySet(), entry -> {
      String unescaped = entry.getKey();
      String expectedEscaped = entry.getValue();
      assertEquals(expectedEscaped, kind.escape(unescaped));
    });
  }

  @Test
  public void escapeSingleQuoted() {
    runInEdtAndWait(() -> {
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
    });
  }

  @Test
  public void escapeTripleSingleQuoted() {
    runInEdtAndWait(() -> {
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
    });
  }

  @Test
  public void escapeDoubleQuoted() {
    runInEdtAndWait(() -> {
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
    });
  }

  @Test
  public void escapeTripleDoubleQuoted() {
    runInEdtAndWait(() -> {
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
    });
  }

  @Test
  public void escapeSlashy() {
    runInEdtAndWait(() -> {
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
    });
  }

  @Test
  public void escapeDollarSlashy() {
    runInEdtAndWait(() -> {
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
    });
  }
}
