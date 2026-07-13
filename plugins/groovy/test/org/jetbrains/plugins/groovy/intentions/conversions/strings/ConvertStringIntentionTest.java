// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.intentions.conversions.strings;

import org.jetbrains.plugins.groovy.intentions.GroovyIntentionsBundle;
import org.jetbrains.plugins.groovy.util.ActionTest;
import org.jetbrains.plugins.groovy.util.GroovyLatestTest;
import org.junit.Test;

import java.util.LinkedHashMap;
import java.util.Map;

public class ConvertStringIntentionTest extends GroovyLatestTest implements ActionTest {
  @Test
  public void convertSingleQuotedToDollarSlashy() {
    LinkedHashMap<String, String> data = new LinkedHashMap<>();
    data.put("/", "/");
    data.put("$", "$$");
    data.put("hello $ world", "hello $ world");
    data.put("hello / world", "hello / world");
    data.put("hello $/ world", "hello $$/ world");
    data.put("hello $$ world", "hello $$$ world");
    data.put("hello /$ world", "hello $/$ world");
    data.put("hello $world", "hello $$world");
    data.put("hello $_world", "hello $$_world");
    for (Map.Entry<String, String> entry : data.entrySet()) {
      doActionTest(GroovyIntentionsBundle.message("convert.to.dollar.slash.regex.intention.name"), "print(<caret>'" + entry.getKey() + "')",
                   "print(<caret>$/" + entry.getValue() + "/$)");
    }
  }

  @Test
  public void convertSlashyToDollarSlashy() {
    LinkedHashMap<String, String> data = new LinkedHashMap<>();
    data.put("\\/", "/");
    data.put("$", "$$");
    data.put("hello $ world", "hello $ world");
    data.put("hello \\/ world", "hello / world");
    data.put("hello $\\/ world", "hello $$/ world");
    data.put("hello $$ world", "hello $$$ world");
    data.put("hello \\/$ world", "hello $/$ world");
    for (Map.Entry<String, String> entry : data.entrySet()) {
      doActionTest(GroovyIntentionsBundle.message("convert.to.dollar.slash.regex.intention.name"), "print(<caret>/" + entry.getKey() + "/)",
                   "print(<caret>$/" + entry.getValue() + "/$)");
    }
  }

  @Test
  public void convertStringWithInjectionToDollarSlashy() {
    LinkedHashMap<String, String> data = new LinkedHashMap<>();
    data.put("/${'/'}u21aF/", "$/${'/'}u21aF/$");
    data.put("/x$ ${'a'} y\\/z/", "$/x$ ${'a'} y/z/$");
    data.put("\"a${'b'}c\"", "$/a${'b'}c/$");
    for (Map.Entry<String, String> entry : data.entrySet()) {
      doActionTest(GroovyIntentionsBundle.message("convert.to.dollar.slash.regex.intention.name"),
                   "print(<caret>" + entry.getKey() + ")", "print(<caret>" + entry.getValue() + ")");
    }
  }

  @Test
  public void convertDollarSlashyToGString() {
    LinkedHashMap<String, String> data = new LinkedHashMap<>();
    data.put("\\u21aF", "\u21aF");
    data.put("\\\\u21aF", "\\\\\\\\u21aF");
    for (Map.Entry<String, String> entry : data.entrySet()) {
      doActionTest(
        GroovyIntentionsBundle.message("convert.string.to.g.string.intention.name"),
        "print(<caret>$/" + entry.getKey() + "/$)",
        "print(<caret>\"" + entry.getValue() + "\")"
      );
    }
  }
}
