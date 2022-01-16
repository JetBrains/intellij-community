// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.idea;

import com.intellij.idea.extensions.BombExplodedExtension;
import org.intellij.lang.annotations.JdkConstants;
import org.junit.jupiter.api.extension.ExtendWith;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
@ExtendWith(BombExplodedExtension.class)
public @interface Bombed {
  int year();
  @JdkConstants.CalendarMonth int month();
  int day();
  int time() default 0;
  String user();
  String description() default "";
}