// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.xmlb.annotations;

import com.intellij.util.xmlb.Constants;
import org.jetbrains.annotations.NonNls;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.TYPE, ElementType.METHOD})
public @interface MapAnnotation {
  boolean surroundWithTag() default true;

  @NonNls String keyAttributeName() default Constants.KEY;

  @NonNls String valueAttributeName() default Constants.VALUE;

  @NonNls String entryTagName() default Constants.ENTRY;

  boolean surroundKeyWithTag() default true;

  boolean surroundValueWithTag() default true;

  boolean sortBeforeSave() default true;
}
