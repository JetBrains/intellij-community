// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.codeInsight.editorActions;

import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.application.impl.NonBlockingReadActionImpl;
import com.intellij.testFramework.fixtures.EditorHintFixture;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.util.GroovyLatestTest;
import org.jetbrains.plugins.groovy.util.ResolveTest;
import org.jetbrains.plugins.groovy.util.TestUtils;
import org.junit.Test;

import java.util.Arrays;
import java.util.stream.Collectors;

public class GroovyParameterInfoHandlerTest extends GroovyLatestTest implements ResolveTest {
  public GroovyParameterInfoHandlerTest() {
    super("parameterInfo/");
  }

  @Test
  public void instance_method_reference_zero_params() {
    testParameterHint();
  }

  @Test
  public void instance_method_reference_single_param() {
    testParameterHint();
  }

  @Test
  public void instance_method_reference_default_param() {
    testParameterHint();
  }

  @Test
  public void instance_method_reference_overloads() {
    testParameterHint();
  }

  @Test
  public void method_with_several_named_params() {
    testParameterHint();
  }

  @Test
  public void method_with_named_params_annotation() {
    testParameterHint();
  }

  private void testParameterHint() {
    String name = uncapitalize(Arrays.stream(getTestName().split("_"))
                                 .map(GroovyParameterInfoHandlerTest::capitalize)
                                 .collect(Collectors.joining(""))) + ".test";

    String input = TestUtils.readInput(getFixture().getTestDataPath() + name).get(0);
    String hint = getParameterHint(input);
    configureByText(input + "\n-----\n" + hint);
    getFixture().checkResultByFile(name);
  }

  private static String capitalize(@Nullable String str) {
    if (str == null || str.isBlank()) return str;
    return str.substring(0, 1).toUpperCase() + str.substring(1).toLowerCase();
  }

  private static String uncapitalize(@Nullable String str) {
    if (str == null || str.isBlank()) return str;
    return str.substring(0, 1).toLowerCase() + str.substring(1);
  }

  private String getParameterHint(String text) {
    configureByText(text);
    EditorHintFixture myHintFixture = new EditorHintFixture(getFixture().getTestRootDisposable());
    getFixture().performEditorAction(IdeActions.ACTION_EDITOR_SHOW_PARAMETER_INFO);
    UIUtil.dispatchAllInvocationEvents();
    waitForParameterInfo();
    return myHintFixture.getCurrentHintText();
  }

  public static void waitForParameterInfo() {
    // effective there is a chain of 3 nonBlockingRead actions
    for (int i = 0; i < 3; i++) {
      UIUtil.dispatchAllInvocationEvents();
      NonBlockingReadActionImpl.waitForAsyncTaskCompletion();
    }
  }
}
