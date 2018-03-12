// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.xmlb.annotations;

import com.intellij.util.xmlb.Constants;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.METHOD})
public @interface XCollection {
  /**
   * The property element name. Defaults to property name if {@link #style} is set to {@link Style#v2}.
   * If not specified and {@link #style} is not specified — property serialized using option tag.
   */
  String propertyElementName() default "";

  /**
   * Value of primitive type wrapped into element named {@code option}. This option allows you to customize element name.
   */
  String elementName() default Constants.OPTION;

  /**
   * Value of primitive type wrapped into element named `option`. This option allows you to customize name of value attribute.
   */
  String valueAttributeName() default Constants.VALUE;

  Class<?>[] elementTypes() default {};

  enum Style {
    /**
     * Old style as AbstractCollection(surroundWithTag = false).
     *
     * <option name="propertyName">
     *   <option value="$value" />
     * </option>
     */
    v1,

    /**
     * Wrap not using option tag (OptionTag), but simple tag (Tag).
     *
     * <propertyName>
     *   <option value="$value" />
     * </propertyName>
     */
    v2,
  }

  Style style() default Style.v1;
}
