// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.codeInsight.editorActions;

import com.intellij.groovy.testFramework.ResolveTest;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.application.impl.NonBlockingReadActionImpl;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.fixtures.EditorHintFixture;
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors;
import org.jetbrains.plugins.groovy.util.TestUtils;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.EdtTestUtil.runInEdtAndWait;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.testNameFixture;
import static org.jetbrains.plugins.groovy.util.GroovyFixture.groovyFixture;

@TestApplication
public class GroovyParameterInfoHandlerTest implements ResolveTest {
  private final TestFixture<JavaCodeInsightTestFixture> groovy = groovyFixture(GroovyProjectDescriptors.GROOVY_2_5, "parameterInfo/");
  private final TestFixture<String> testName = testNameFixture(false);

  @Override
  public @NotNull JavaCodeInsightTestFixture getFixture() {
    return groovy.get();
  }

  public String getTestName() {
    return testName.get();
  }

  @Test
  public void instanceMethodReferenceZeroParams() {
    runInEdtAndWait(() -> testParameterHint());
  }

  @Test
  public void instanceMethodReferenceSingleParam() {
    runInEdtAndWait(() -> testParameterHint());
  }

  @Test
  public void instanceMethodReferenceDefaultParam() {
    runInEdtAndWait(() -> testParameterHint());
  }

  @Test
  public void instanceMethodReferenceOverloads() {
    runInEdtAndWait(() -> testParameterHint());
  }

  @Test
  public void methodWithSeveralNamedParams() {
    runInEdtAndWait(() -> testParameterHint());
  }

  @Test
  public void methodWithNamedParamsAnnotation() {
    runInEdtAndWait(() -> testParameterHint());
  }

  @Test
  public void closureWithTypedParams() {
    runInEdtAndWait(() -> testParameterHint());
  }

  @Test
  public void closureWithUntypedParams() {
    runInEdtAndWait(() -> testParameterHint());
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
    PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();
    waitForParameterInfo();
    return myHintFixture.getCurrentHintText();
  }

  public static void waitForParameterInfo() {
    // effective there is a chain of 3 nonBlockingRead actions
    for (int i = 0; i < 3; i++) {
      PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();
      NonBlockingReadActionImpl.waitForAsyncTaskCompletion();
    }
  }
}
