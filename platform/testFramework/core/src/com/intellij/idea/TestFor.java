// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.idea;

import org.jetbrains.annotations.NotNull;

import java.lang.annotation.*;

/**
 * Helps binding production classes and issue IDs with tests code
 * to help reading the code and search for usages
 */
@Retention(RetentionPolicy.SOURCE)
@Target({ElementType.TYPE, ElementType.METHOD})
public @interface TestFor {
  /**
   * Binds implementation class with a test to let
   * find-usages help locating class by test
   */
  Class @NotNull [] classes() default {};

  /**
   * Binds test with issues
   */
  String @NotNull [] issues() default {};
}
