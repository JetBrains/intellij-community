// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.idea;

import org.intellij.lang.annotations.JdkConstants;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * @deprecated Previously, that was a wrong way to mute the test temporarily, as it may suddenly start failing
 * in stable branches. To ignore test completely, use {@link org.junit.Ignore} or {@link IgnoreJUnit3}. 
 * Otherwise, use features of CI server (e.g., mute test with unmute on specific date)
 * Since November 2023, this annotation is useless and doesn't affect tests invocation. Please don't use it.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
@Deprecated(forRemoval = true)
public @interface Bombed {
  int year();
  @JdkConstants.CalendarMonth int month();
  int day();
  int time() default 0;
  String user();
  String description() default "";
}