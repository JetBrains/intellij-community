/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.idea;

import org.intellij.lang.annotations.JdkConstants;

import java.lang.annotation.*;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
@Inherited
public @interface Bombed {
  int year() default 2018;
  @JdkConstants.CalendarMonth int month();
  int day();
  int time() default 0;
  String user();
  String description() default "";
}