// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.resolve;

import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.jetbrains.plugins.groovy.util.Groovy30Test;
import org.jetbrains.plugins.groovy.util.ResolveTest;
import org.junit.Test;

public class ResolveLocalTest extends Groovy30Test implements ResolveTest {
  @Test
  public void resource_variable_from_try_block() {
    resolveTest("try (def a) { println <caret>a }", GrVariable.class);
  }

  @Test
  public void resource_variable_from_catch_block() {
    resolveTest("try (def a) {} catch(e) { println <caret>a }", null);
  }

  @Test
  public void resource_variable_from_finally_block() {
    resolveTest("try (def a) {} finally { println <caret>a }", null);
  }

  @Test
  public void resource_variable_from_another_resource() {
    resolveTest("try (def a; def b = <caret>a) {}", GrVariable.class);
  }

  @Test
  public void forward_resource_variable() {
    resolveTest("try (def b = <caret>a; def a) {}", null);
  }

  @Test
  public void parameter_from_resource_initializer() {
    resolveTest("def foo(param) { try (def a = <caret>param) {} }", GrParameter.class);
  }

  @Test
  public void for_variable_from_for_block() {
    resolveTest("for (def e,f;;){ <caret>e }", GrVariable.class);
    resolveTest("for (def e,f;;){ <caret>f }", GrVariable.class);
    resolveTest("for (def (e,f);;){ <caret>e }", GrVariable.class);
    resolveTest("for (def (e,f);;){ <caret>f }", GrVariable.class);
  }

  @Test
  public void for_variable_from_for_condition() {
    resolveTest("for (def e,f; <caret>e;){}", GrVariable.class);
    resolveTest("for (def e,f; <caret>f;){}", GrVariable.class);
    resolveTest("for (def (e,f); <caret>e;){}", GrVariable.class);
    resolveTest("for (def (e,f); <caret>f;){}", GrVariable.class);
  }

  @Test
  public void for_variable_from_for_update() {
    resolveTest("for (def e,f;; <caret>e){}", GrVariable.class);
    resolveTest("for (def e,f;; <caret>f){}", GrVariable.class);
    resolveTest("for (def (e,f);; <caret>e){}", GrVariable.class);
    resolveTest("for (def (e,f);; <caret>f){}", GrVariable.class);
  }

  @Test
  public void for_variable_from_another_variable() {
    resolveTest("for (def e,f = <caret>e;;) {}", GrVariable.class);
    resolveTest("for (def f = <caret>e, e;;) {}", null);
  }

  @Test
  public void for_variable_from_for_each_expression() {
    resolveTest("for (e : <caret>e) {}", null);
    resolveTest("for (e in <caret>e) {}", null);
  }

  @Test
  public void for_variable_from_for_each_block() {
    resolveTest("for (e : b) { <caret>e }", GrVariable.class);
    resolveTest("for (e in b) { <caret>e }", GrVariable.class);
  }

  @Test
  public void for_variable_after_for() {
    resolveTest("for (def e;;) {}; <caret>e", null);
    resolveTest("for (e : b) {}; <caret>e", null);
    resolveTest("for (e in b) {}; <caret>e", null);
  }

  @Test
  public void lambda_parameter() {
    resolveTest("def l = a -> <caret>a ", GrParameter.class);
    resolveTest("def l = (a) -> <caret>a ", GrParameter.class);
    resolveTest("def l = (Integer a, def b) -> {a; <caret>b} ", GrParameter.class);
    resolveTest("def l = a -> {<caret>a} ", GrParameter.class);
  }

  @Test
  public void variable_after_lambda() {
    resolveTest("def l = a -> a; <caret>a ", null);
    resolveTest("def l = (a) -> a; <caret>a ", null);
    resolveTest("def l = (Integer a) -> {}; <caret>a ", null);
    resolveTest("def l = a -> {}; <caret>a ", null);
  }

  @Test
  public void outer_parameter_in_lambda() {
    resolveTest("def foo(param) { def l = a -> <caret>param }", GrParameter.class);
    resolveTest("def foo(param) { def l = a -> {<caret>param} }", GrParameter.class);
    resolveTest("def foo(param) { def l = (a = <caret>param) -> <caret>param }", GrParameter.class);
  }

  @Test
  public void local_variable_inside_lambda() {
    resolveTest("def l = a -> {def param; <caret>param }", GrVariable.class);
  }
}
