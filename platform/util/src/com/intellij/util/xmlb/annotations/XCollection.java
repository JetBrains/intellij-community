/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.util.xmlb.annotations;

import com.intellij.util.xmlb.Constants;
import org.jetbrains.annotations.ApiStatus;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * ```xml
 * <option value="$value" />
 * ... n item elements
 * ```
 *
 * Where `option` it is item element (use `elementName` to customize element name) and
 * `value` it is value attribute (use `valueAttributeName` to customize attribute name).
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.METHOD})
@ApiStatus.Experimental
public @interface XCollection {
  /**
   * The property element name. Defaults to property name if `style = v2`.
   * If not specified and `style` is not specified — property serialized using option tag.
   */
  String propertyElementName() default "";

  /**
   * Value of primitive type wrapped into element named `option`. This option allows you to customize element name.
   * For example, for `elementName = "module"`:
   *
   * <module value="$value" />
   */
  String elementName() default Constants.OPTION;

  /**
   * Value of primitive type wrapped into element named `option`. This option allows you to customize name of value attribute.
   * For example, for `valueAttributeName = "name"`:
   *
   * <option name="$value" />
   *
   * Empty name is allowed — in this case value will be serialized as element text.
   * For example, for `valueAttributeName = ""`:
   *
   * <option>
   *   $value
   * </option>
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
