// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.actions.generate;

import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.CustomizedDataContext;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.TestActionEvent;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.plugins.groovy.actions.generate.GrBaseGenerateAction;
import org.jetbrains.plugins.groovy.actions.generate.accessors.GroovyGenerateGetterAction;
import org.jetbrains.plugins.groovy.actions.generate.accessors.GroovyGenerateGetterSetterAction;
import org.jetbrains.plugins.groovy.actions.generate.accessors.GroovyGenerateSetterAction;
import org.jetbrains.plugins.groovy.actions.generate.constructors.GroovyGenerateConstructorAction;
import org.jetbrains.plugins.groovy.actions.generate.equals.GroovyGenerateEqualsAction;
import org.jetbrains.plugins.groovy.actions.generate.missing.GroovyGenerateMethodMissingAction;
import org.jetbrains.plugins.groovy.actions.generate.missing.GroovyGeneratePropertyMissingAction;
import org.jetbrains.plugins.groovy.actions.generate.tostring.GroovyGenerateToStringAction;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestApplication
public class GroovyGenerateActionsTest {
  private static final TestFixture<LightJavaProject> project = lightJavaProjectFixture(LightProjectDescriptor.EMPTY_PROJECT_DESCRIPTOR);
  private final TestFixture<LightJavaCodeInsightTestFixture> groovy = lightJavaCodeInsightFixture(project);

  @Test
  public void test_generate_actions_enabled_for_groovy_files() {
    groovy.get().configureByText("a.groovy", """
      class Test {
        def field
        def anotherField

        <caret>
      }
      """);
    ArrayList<GrBaseGenerateAction> actions = new ArrayList<>(
      Arrays.asList(new GroovyGenerateEqualsAction(), new GroovyGenerateGetterAction(), new GroovyGenerateSetterAction(),
                    new GroovyGenerateGetterSetterAction(), new GroovyGenerateConstructorAction(),
                    new GroovyGeneratePropertyMissingAction(), new GroovyGenerateMethodMissingAction(),
                    new GroovyGenerateToStringAction()));

    EdtTestUtil.runInEdtAndWait(() -> {
      for (GrBaseGenerateAction action : actions) {
        AnActionEvent event = TestActionEvent.createTestEvent(action, getCurrentEditorDataContext());
        action.update(event);
        assertTrue(event.getPresentation().isEnabledAndVisible());
      }
    });
  }

  @Test
  public void test_generate_actions_disabled_for_non_groovy_files() {
    groovy.get().configureByText("a.java", """
      class Test {
        String field;
        int anotherField;

        <caret>
      }
      """);
    ArrayList<GrBaseGenerateAction> actions = new ArrayList<>(
      Arrays.asList(new GroovyGenerateEqualsAction(), new GroovyGenerateGetterAction(), new GroovyGenerateSetterAction(),
                    new GroovyGenerateGetterSetterAction(), new GroovyGenerateConstructorAction(),
                    new GroovyGeneratePropertyMissingAction(), new GroovyGenerateMethodMissingAction(),
                    new GroovyGenerateToStringAction()));

    EdtTestUtil.runInEdtAndWait(() -> {
      for (GrBaseGenerateAction action : actions) {
        AnActionEvent event = TestActionEvent.createTestEvent(action, getCurrentEditorDataContext());
        action.update(event);
        assertFalse(event.getPresentation().isEnabledAndVisible());
      }
    });
  }

  /** The data context of the editor, as {@code LightPlatformCodeInsightTestCase.getCurrentEditorDataContext} makes it. */
  private DataContext getCurrentEditorDataContext() {
    DataContext defaultContext = DataManager.getInstance().getDataContext();
    return CustomizedDataContext.withSnapshot(defaultContext, sink -> {
      sink.set(CommonDataKeys.EDITOR, groovy.get().getEditor());
      sink.set(CommonDataKeys.PROJECT, groovy.get().getProject());
      sink.set(CommonDataKeys.PSI_FILE, groovy.get().getFile());
      sink.lazy(CommonDataKeys.PSI_ELEMENT, () -> groovy.get().getFile().findElementAt(groovy.get().getCaretOffset()));
    });
  }
}
