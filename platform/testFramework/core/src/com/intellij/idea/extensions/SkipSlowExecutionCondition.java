// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.idea.extensions;

import com.intellij.testFramework.SkipSlowTestLocally;
import com.intellij.testFramework.TestFrameworkUtil;
import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import org.junit.jupiter.api.extension.ExecutionCondition;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.platform.commons.support.AnnotationSupport;

public class SkipSlowExecutionCondition implements ExecutionCondition {
  private static final ConditionEvaluationResult ENABLED = ConditionEvaluationResult.enabled("Enabled locally");
  private static final ConditionEvaluationResult DISABLED = ConditionEvaluationResult.disabled("Slow tests are disabled locally");

  @Override
  public ConditionEvaluationResult evaluateExecutionCondition(ExtensionContext context) {
    return TestFrameworkUtil.SKIP_SLOW && AnnotationSupport.findAnnotation(context.getTestClass(), SkipSlowTestLocally.class).isPresent() ? DISABLED : ENABLED;
  }
}
