// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.codeInsight.editorActions;

import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.application.impl.NonBlockingReadActionImpl;
import com.intellij.testFramework.fixtures.EditorHintFixture;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.plugins.groovy.util.GroovyLatestTest;
import org.jetbrains.plugins.groovy.util.ResolveTest;
import org.jetbrains.plugins.groovy.util.TestUtils;
import org.junit.Test;


public class GroovyParameterInfoHandlerTest extends GroovyLatestTest implements ResolveTest {
  public GroovyParameterInfoHandlerTest() {
    super("parameterInfo/");
  }

  @Test
  public void instanceMethodReferenceZeroParams() {
    testParameterHint();
  }

  @Test
  public void instanceMethodReferenceSingleParam() {
    testParameterHint();
  }

  @Test
  public void instanceMethodReferenceDefaultParam() {
    testParameterHint();
  }

  @Test
  public void instanceMethodReferenceOverloads() {
    testParameterHint();
  }

  @Test
  public void methodWithSeveralNamedParams() {
    testParameterHint();
  }

  @Test
  public void methodWithNamedParamsAnnotation() {
    testParameterHint();
  }

  @Test
  public void closureWithTypedParams() {
    testParameterHint();
  }

  @Test
  public void closureWithUntypedParams() {
    testParameterHint();
  }

  private void testParameterHint() {
    String name = getTestName() + ".test";

    String input = TestUtils.readInput(getFixture().getTestDataPath() + name).get(0);
    String hint = getParameterHint(input);
    configureByText(input + "\n-----\n" + hint);
    getFixture().checkResultByFile(name);
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
