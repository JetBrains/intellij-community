// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.eventLog.validator.rules.impl;

import com.intellij.internal.statistic.eventLog.validator.rules.FUSRule;
import com.intellij.internal.statistic.eventLog.validator.rules.utils.UtilRuleProducer;

/**
 * Use this class to validate data with java code when {@link EnumValidationRule} or {@link RegexpValidationRule} rules are not enough,
 * e.g there are too many possible values or they are dynamically generated.
 * Make sure that your {@link UtilRuleProducer} is able to create new validation rule.
 *
 * Example: {util#class_name} checks that the value is a class name from platform, JB plugin or a plugin from JB plugin repository.
 */
public interface UtilValidationRule extends FUSRule {
}
