// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.xmlb.annotations;

import com.intellij.util.xmlb.SerializationFilter;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.TYPE, ElementType.METHOD})
public @interface Property {
  boolean surroundWithTag() default true;

  /**
   * Serialize into parent element. Allowed only for bean properties (not primitive types).
   */
  boolean flat() default false;

  Class<? extends SerializationFilter> filter() default SerializationFilter.class;

  boolean alwaysWrite() default false;

  enum Style {
    /**
     * Wrap properties into elements named {@code option}.
     */
    OPTION_TAG,
    /**
     * Wrap properties into attributes.
     */
    ATTRIBUTE
  }

  /**
   * Applicable only on class level.
   */
  Style style() default Style.OPTION_TAG;

  /**
   * Whether to assert that class has at least one serializable property.
   */
  boolean assertIfNoBindings() default true;
}
