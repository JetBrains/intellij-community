/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.util.xmlb.annotations;

import com.intellij.util.xmlb.Constants;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * @deprecated Use {@link XCollection}
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.METHOD})
public @interface AbstractCollection {
  /**
   * @return whether all collection items should be surrounded with a single tag
   */
  boolean surroundWithTag() default true;

  /**
   * Due to historical reasons even LinkedHashSet will be sorted according to the natural ordering of its elements.
   */
  boolean sortOrderedSet() default true;

  String elementTag() default Constants.OPTION;
  String elementValueAttribute() default Constants.VALUE;

  Class[] elementTypes() default {};
}
