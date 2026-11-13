// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.intentions;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.groovy.testFramework.BaseTest;
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.intellij.testFramework.EdtTestUtil.runInEdtAndWait;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.jetbrains.plugins.groovy.util.GroovyFixture.groovyFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestApplication
public class MakeClosureCallExplicitIntentionTest implements BaseTest {
  private static final TestFixture<LightJavaProject> project = lightJavaProjectFixture(GroovyProjectDescriptors.GROOVY_2_5);
  private final TestFixture<LightJavaCodeInsightTestFixture> groovy = groovyFixture(project);

  @Override
  public @NotNull JavaCodeInsightTestFixture getFixture() {
    return groovy.get();
  }

  private void doTest(String before, String after) {
    configureByText(before);
    List<IntentionAction> intentions =
      getFixture().filterAvailableIntentions(GroovyIntentionsBundle.message("make.closure.call.explicit.intention.name"));
    if (after == null) {
      assertTrue(intentions.isEmpty());
    }
    else {
      assertEquals(1, intentions.size());
      getFixture().launchAction(intentions.get(0));
      getFixture().checkResult(after);
    }
  }

  @Test
  public void closure_local_variable() {
    runInEdtAndWait(() -> {
      doTest("def local = {}; <caret>local()", "def local = {}; <caret>local.call()");
    });
  }

  @Test
  public void non_closure_local_variable() {
    runInEdtAndWait(() -> {
      doTest("def local = \"\"; <caret>local()", null);
    });
  }

  @Test
  public void callable_local_variable() {
    runInEdtAndWait(() -> {
      getFixture().addFileToProject("classes.groovy", "class Callable { def call() {} }");
      doTest("def local = new Callable(); <caret>local()", null);
    });
  }

  @Test
  public void closure_property() {
    runInEdtAndWait(() -> {
      getFixture().addFileToProject("classes.groovy", "class A { def prop = {} }");
      doTest("new A().<caret>prop()", "new A().<caret>prop.call()");
    });
  }

  @Test
  public void non_closure_property() {
    runInEdtAndWait(() -> {
      getFixture().addFileToProject("classes.groovy", "class A { def prop = \"\" }");
      doTest("new A().<caret>prop()", null);
    });
  }

  @Test
  public void closure_method() {
    runInEdtAndWait(() -> {
      doTest("Closure foo() {}; <caret>foo()", null);
    });
  }

  @Test
  public void closure_method_call() {
    runInEdtAndWait(() -> {
      doTest("Closure foo() {}; foo().<caret>call()", null);
    });
  }
}
