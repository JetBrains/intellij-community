// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.intentions.conversions.strings;

import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors;
import org.jetbrains.plugins.groovy.intentions.GroovyIntentionsBundle;
import org.jetbrains.plugins.groovy.util.ActionTest;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static com.intellij.testFramework.EdtTestUtil.runInEdtAndWait;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.jetbrains.plugins.groovy.util.GroovyFixture.groovyFixture;

@TestApplication
public class ConvertStringIntentionTest implements ActionTest {
  private static final TestFixture<LightJavaProject> project = lightJavaProjectFixture(GroovyProjectDescriptors.GROOVY_2_5);
  private final TestFixture<LightJavaCodeInsightTestFixture> groovy = groovyFixture(project);

  @Override
  public @NotNull JavaCodeInsightTestFixture getFixture() {
    return groovy.get();
  }

  @Test
  public void convertSingleQuotedToDollarSlashy() {
    runInEdtAndWait(() -> {
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
    });
  }

  @Test
  public void convertSlashyToDollarSlashy() {
    runInEdtAndWait(() -> {
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
    });
  }

  @Test
  public void convertStringWithInjectionToDollarSlashy() {
    runInEdtAndWait(() -> {
      LinkedHashMap<String, String> data = new LinkedHashMap<>();
      data.put("/${'/'}u21aF/", "$/${'/'}u21aF/$");
      data.put("/x$ ${'a'} y\\/z/", "$/x$ ${'a'} y/z/$");
      data.put("\"a${'b'}c\"", "$/a${'b'}c/$");
      for (Map.Entry<String, String> entry : data.entrySet()) {
        doActionTest(GroovyIntentionsBundle.message("convert.to.dollar.slash.regex.intention.name"),
                     "print(<caret>" + entry.getKey() + ")", "print(<caret>" + entry.getValue() + ")");
      }
    });
  }

  @Test
  public void convertSlashyToString() {
    runInEdtAndWait(() -> {
      LinkedHashMap<String, String> data = new LinkedHashMap<>();
      data.put("asdf", "asdf");
      data.put("hello\\/world", "hello/world");
      for (Map.Entry<String, String> entry : data.entrySet()) {
        doActionTest(GroovyIntentionsBundle.message("convert.g.string.to.string.intention.name"),
                     "print(<caret>/" + entry.getKey() + "/)",
                     "print(<caret>'" + entry.getValue() + "')");
      }
    });
  }

  @Test
  public void convertDollarSlashyToString() {
    runInEdtAndWait(() -> {
      LinkedHashMap<String, String> data = new LinkedHashMap<>();
      data.put("asdf", "asdf");
      data.put("\\u0041", "A");
      for (Map.Entry<String, String> entry : data.entrySet()) {
        doActionTest(GroovyIntentionsBundle.message("convert.g.string.to.string.intention.name"),
                     "print(<caret>$/" + entry.getKey() + "/$)",
                     "print(<caret>'" + entry.getValue() + "')");
      }
    });
  }

  @Test
  public void convertDollarSlashyToGString() {
    runInEdtAndWait(() -> {
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
    });
  }
}
