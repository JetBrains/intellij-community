// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.idea.extensions;

import com.intellij.idea.IJIgnore;
import org.junit.jupiter.api.extension.*;
import org.junit.platform.commons.support.AnnotationSupport;

public class IJIgnoreExtension implements ExecutionCondition {
  @Override
  public ConditionEvaluationResult evaluateExecutionCondition(ExtensionContext context) {
    return AnnotationSupport.findAnnotation(context.getElement(), IJIgnore.class)
      .map(annotation -> ConditionEvaluationResult.disabled("Test is ignored, linked issue: '" + annotation.issue() + "'"))
      .orElse(ConditionEvaluationResult.enabled("Test is not ignored"));
  }
}
