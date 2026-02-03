// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.grazie.pro;

import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import org.junit.jupiter.api.extension.ExecutionCondition;
import org.junit.jupiter.api.extension.ExtensionContext;

import java.lang.reflect.AnnotatedElement;

public class NeedsCloudExtension implements ExecutionCondition {
  @Override
  public ConditionEvaluationResult evaluateExecutionCondition(ExtensionContext context) {
    if ("true".equals(System.getProperty("ignore.grazie.pro.cloud.tests.in.ai.assistant"))) {
      AnnotatedElement element = context.getElement().orElse(null);
      if (element != null && element.isAnnotationPresent(NeedsCloud.class)) {
        return ConditionEvaluationResult.disabled("Test disabled as needing cloud");
      }
    }
    return ConditionEvaluationResult.enabled("Test enabled by default");
  }
}