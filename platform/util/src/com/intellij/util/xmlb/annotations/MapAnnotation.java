/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package com.intellij.util.xmlb.annotations;

import com.intellij.util.xmlb.Constants;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.TYPE, ElementType.METHOD})
public @interface MapAnnotation  {
  boolean surroundWithTag() default true;

  String keyAttributeName() default Constants.KEY;
  String valueAttributeName() default Constants.VALUE;
  String entryTagName() default Constants.ENTRY;

  boolean surroundKeyWithTag() default true;
  boolean surroundValueWithTag() default true;

  boolean sortBeforeSave() default true;

  String propertyElementName() default "";
}
