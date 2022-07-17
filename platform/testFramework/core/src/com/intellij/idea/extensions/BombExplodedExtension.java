// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.idea.extensions;

import com.intellij.idea.Bombed;
import com.intellij.testFramework.TestFrameworkUtil;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.extension.*;
import org.junit.platform.commons.support.AnnotationSupport;

public class BombExplodedExtension implements BeforeEachCallback, BeforeAllCallback, ExecutionCondition {
  @Override
  public void beforeEach(ExtensionContext context) {
    explodeBomb(context);
  }

  @Override
  public void beforeAll(ExtensionContext context) {
    explodeBomb(context);
  }

  @Override
  public ConditionEvaluationResult evaluateExecutionCondition(ExtensionContext context) {
   	return AnnotationSupport.findAnnotation(context.getElement(), Bombed.class)
				.map(bombed -> TestFrameworkUtil.bombExplodes(bombed) 
                       ? ConditionEvaluationResult.enabled("Bomb exploded") 
                       : ConditionEvaluationResult.disabled("Bomb not yet exploded"))
				.orElse(ConditionEvaluationResult.enabled("No bomb defined"));
  }

  private static void explodeBomb(ExtensionContext context) {
    AnnotationSupport.findAnnotation(context.getElement(), Bombed.class)
      .ifPresent(bombed -> {
        if (TestFrameworkUtil.bombExplodes(bombed)) {
          String description = bombed.description().isEmpty() ? "" : " (" + bombed.description() + ")";
          Assertions.fail("Bomb created by " + bombed.user() + description + " now explodes");
        }
      });
  }
}
