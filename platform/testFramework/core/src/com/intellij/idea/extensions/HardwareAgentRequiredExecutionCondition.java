// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.idea.extensions;

import com.intellij.TestCaseLoader;
import com.intellij.idea.HardwareAgentRequired;
import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import org.junit.jupiter.api.extension.ExecutionCondition;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.platform.commons.support.AnnotationSupport;

public class HardwareAgentRequiredExecutionCondition implements ExecutionCondition {

  private static final boolean RUN_WITH_HARDWARE_REQUIREMENT = Boolean.getBoolean(TestCaseLoader.HARDWARE_AGENT_REQUIRED_FLAG);
  private static final boolean IS_LOCAL_RUN = System.getenv("TEAMCITY_VERSION") == null;

  private static final ConditionEvaluationResult ENABLED_LOCALLY = ConditionEvaluationResult.enabled("Enabled locally");
  private static final ConditionEvaluationResult MET_HARDWARE_REQUIREMENT = ConditionEvaluationResult.enabled("Hardware requirement met");
  private static final ConditionEvaluationResult UNMET_HARDWARE_REQUIREMENT = ConditionEvaluationResult.disabled("Unmet hardware requirement");

  @Override
  public ConditionEvaluationResult evaluateExecutionCondition(ExtensionContext context) {
    if (IS_LOCAL_RUN) {
      return ENABLED_LOCALLY;
    }
    boolean hasAnnotation = AnnotationSupport.findAnnotation(context.getTestClass(), HardwareAgentRequired.class).isPresent();
    return hasAnnotation == RUN_WITH_HARDWARE_REQUIREMENT
           ? MET_HARDWARE_REQUIREMENT 
           : UNMET_HARDWARE_REQUIREMENT;
  }
}
