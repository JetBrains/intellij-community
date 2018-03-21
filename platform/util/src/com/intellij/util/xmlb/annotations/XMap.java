// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.xmlb.annotations;

import com.intellij.util.xmlb.Constants;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.TYPE, ElementType.METHOD})
public @interface XMap {
  /**
   * The property element name.
   */
  String propertyElementName() default "";

  String entryTagName() default Constants.ENTRY;

  String keyAttributeName() default Constants.KEY;

  String valueAttributeName() default Constants.VALUE;
}
