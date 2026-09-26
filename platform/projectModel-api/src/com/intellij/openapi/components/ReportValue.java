// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.components;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Indicates that an absolute value of the field should be reported in statistics.
 * Won't work on objects.
 * To record string field enumerate all possible values in {@link #possibleValues()}
 *
 * Can be used within persistent components if reportStatistics flag is enabled.
 * @see State#reportStatistic()
 */
@Target({ElementType.FIELD, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface ReportValue {

  /**
   * Describes possible values of field that should be recorded in statistics.
   * Applies only to string fields.
   */
  String[] possibleValues() default {};
}
