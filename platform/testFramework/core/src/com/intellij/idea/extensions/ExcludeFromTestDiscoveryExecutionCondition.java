// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.idea.extensions;

import com.intellij.idea.ExcludeFromTestDiscovery;
import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import org.junit.jupiter.api.extension.ExecutionCondition;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.platform.commons.support.AnnotationSupport;

public class ExcludeFromTestDiscoveryExecutionCondition implements ExecutionCondition {

  private static final boolean NOT_DISCOVERY_RUN = System.getProperty("test.discovery.listener") == null;
  private static final ConditionEvaluationResult NOT_DISCOVERY_RUN_ALWAYS_ENABLED = ConditionEvaluationResult.enabled("Not discovery run");

  private static final ConditionEvaluationResult EXCLUDED_FROM_TEST_DISCOVERY = ConditionEvaluationResult.disabled("Excluded from test discovery");
  private static final ConditionEvaluationResult INCLUDED_IN_TEST_DISCOVERY = ConditionEvaluationResult.enabled("Included in test discovery");

  @Override
  public ConditionEvaluationResult evaluateExecutionCondition(ExtensionContext context) {
    if (NOT_DISCOVERY_RUN) {
      return NOT_DISCOVERY_RUN_ALWAYS_ENABLED;
    }
    return AnnotationSupport.findAnnotation(context.getTestClass(), ExcludeFromTestDiscovery.class)
             .map(a -> EXCLUDED_FROM_TEST_DISCOVERY)
      .orElse(INCLUDED_IN_TEST_DISCOVERY);
  }
}
