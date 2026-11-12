// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.resolve;

import com.intellij.groovy.testFramework.ResolveTest;
import com.intellij.openapi.project.Project;
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.EdtTestUtil.runInEdtAndWait;
import static org.jetbrains.plugins.groovy.util.GroovyFixture.groovyFixture;
import static org.jetbrains.plugins.groovy.util.GroovyFixture.groovyProjectFixture;

@TestApplication
public class ResolveLocalTest implements ResolveTest {
  private static final TestFixture<Project> project = groovyProjectFixture();
  private final TestFixture<JavaCodeInsightTestFixture> groovy = groovyFixture(project, GroovyProjectDescriptors.GROOVY_3_0);

  @Override
  public @NotNull JavaCodeInsightTestFixture getFixture() {
    return groovy.get();
  }

  @Test
  public void resource_variable_from_try_block() {
    runInEdtAndWait(() -> resolveTest("try (def a) { println <caret>a }", GrVariable.class));
  }

  @Test
  public void resource_variable_from_catch_block() {
    runInEdtAndWait(() -> resolveTest("try (def a) {} catch(e) { println <caret>a }", null));
  }

  @Test
  public void resource_variable_from_finally_block() {
    runInEdtAndWait(() -> resolveTest("try (def a) {} finally { println <caret>a }", null));
  }

  @Test
  public void resource_variable_from_another_resource() {
    runInEdtAndWait(() -> resolveTest("try (def a; def b = <caret>a) {}", GrVariable.class));
  }

  @Test
  public void forward_resource_variable() {
    runInEdtAndWait(() -> resolveTest("try (def b = <caret>a; def a) {}", null));
  }

  @Test
  public void parameter_from_resource_initializer() {
    runInEdtAndWait(() -> resolveTest("def foo(param) { try (def a = <caret>param) {} }", GrParameter.class));
  }

  @Test
  public void for_variable_from_for_block() {
    runInEdtAndWait(() -> {
      resolveTest("for (def e,f;;){ <caret>e }", GrVariable.class);
      resolveTest("for (def e,f;;){ <caret>f }", GrVariable.class);
      resolveTest("for (def (e,f);;){ <caret>e }", GrVariable.class);
      resolveTest("for (def (e,f);;){ <caret>f }", GrVariable.class);
    });
  }

  @Test
  public void for_variable_from_for_condition() {
    runInEdtAndWait(() -> {
      resolveTest("for (def e,f; <caret>e;){}", GrVariable.class);
      resolveTest("for (def e,f; <caret>f;){}", GrVariable.class);
      resolveTest("for (def (e,f); <caret>e;){}", GrVariable.class);
      resolveTest("for (def (e,f); <caret>f;){}", GrVariable.class);
    });
  }

  @Test
  public void for_variable_from_for_update() {
    runInEdtAndWait(() -> {
      resolveTest("for (def e,f;; <caret>e){}", GrVariable.class);
      resolveTest("for (def e,f;; <caret>f){}", GrVariable.class);
      resolveTest("for (def (e,f);; <caret>e){}", GrVariable.class);
      resolveTest("for (def (e,f);; <caret>f){}", GrVariable.class);
    });
  }

  @Test
  public void for_variable_from_another_variable() {
    runInEdtAndWait(() -> {
      resolveTest("for (def e,f = <caret>e;;) {}", GrVariable.class);
      resolveTest("for (def f = <caret>e, e;;) {}", null);
    });
  }

  @Test
  public void for_variable_from_for_each_expression() {
    runInEdtAndWait(() -> {
      resolveTest("for (e : <caret>e) {}", null);
      resolveTest("for (e in <caret>e) {}", null);
    });
  }

  @Test
  public void for_variable_from_for_each_block() {
    runInEdtAndWait(() -> {
      resolveTest("for (e : b) { <caret>e }", GrVariable.class);
      resolveTest("for (e in b) { <caret>e }", GrVariable.class);
    });
  }

  @Test
  public void for_variable_after_for() {
    runInEdtAndWait(() -> {
      resolveTest("for (def e;;) {}; <caret>e", null);
      resolveTest("for (e : b) {}; <caret>e", null);
      resolveTest("for (e in b) {}; <caret>e", null);
    });
  }

  @Test
  public void lambda_parameter() {
    runInEdtAndWait(() -> {
      resolveTest("def l = a -> <caret>a ", GrParameter.class);
      resolveTest("def l = (a) -> <caret>a ", GrParameter.class);
      resolveTest("def l = (Integer a, def b) -> {a; <caret>b} ", GrParameter.class);
      resolveTest("def l = a -> {<caret>a} ", GrParameter.class);
    });
  }

  @Test
  public void variable_after_lambda() {
    runInEdtAndWait(() -> {
      resolveTest("def l = a -> a; <caret>a ", null);
      resolveTest("def l = (a) -> a; <caret>a ", null);
      resolveTest("def l = (Integer a) -> {}; <caret>a ", null);
      resolveTest("def l = a -> {}; <caret>a ", null);
    });
  }

  @Test
  public void outer_parameter_in_lambda() {
    runInEdtAndWait(() -> {
      resolveTest("def foo(param) { def l = a -> <caret>param }", GrParameter.class);
      resolveTest("def foo(param) { def l = a -> {<caret>param} }", GrParameter.class);
      resolveTest("def foo(param) { def l = (a = <caret>param) -> <caret>param }", GrParameter.class);
    });
  }

  @Test
  public void local_variable_inside_lambda() {
    runInEdtAndWait(() -> resolveTest("def l = a -> {def param; <caret>param }", GrVariable.class));
  }
}
