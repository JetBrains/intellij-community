// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.actions.generate;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.testFramework.LightPlatformCodeInsightTestCase;
import com.intellij.testFramework.TestActionEvent;
import org.jetbrains.plugins.groovy.actions.generate.GrBaseGenerateAction;
import org.jetbrains.plugins.groovy.actions.generate.accessors.GroovyGenerateGetterAction;
import org.jetbrains.plugins.groovy.actions.generate.accessors.GroovyGenerateGetterSetterAction;
import org.jetbrains.plugins.groovy.actions.generate.accessors.GroovyGenerateSetterAction;
import org.jetbrains.plugins.groovy.actions.generate.constructors.GroovyGenerateConstructorAction;
import org.jetbrains.plugins.groovy.actions.generate.equals.GroovyGenerateEqualsAction;
import org.jetbrains.plugins.groovy.actions.generate.missing.GroovyGenerateMethodMissingAction;
import org.jetbrains.plugins.groovy.actions.generate.missing.GroovyGeneratePropertyMissingAction;
import org.jetbrains.plugins.groovy.actions.generate.tostring.GroovyGenerateToStringAction;

import java.util.ArrayList;
import java.util.Arrays;

public class GroovyGenerateActionsTest extends LightPlatformCodeInsightTestCase {
  public void test_generate_actions_enabled_for_groovy_files() {
    configureFromFileText("a.groovy", """
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

    for (GrBaseGenerateAction action : actions) {
      AnActionEvent event = TestActionEvent.createTestEvent(action, getCurrentEditorDataContext());
      action.update(event);
      assert event.getPresentation().isEnabledAndVisible();
    }
  }

  public void test_generate_actions_disabled_for_non_groovy_files() {
    configureFromFileText("a.java", """
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

    for (GrBaseGenerateAction action : actions) {
      AnActionEvent event = TestActionEvent.createTestEvent(action, getCurrentEditorDataContext());
      action.update(event);
      assert !event.getPresentation().isEnabledAndVisible();
    }
  }
}
