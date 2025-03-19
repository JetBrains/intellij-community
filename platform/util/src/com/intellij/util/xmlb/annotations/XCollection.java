// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.xmlb.annotations;

import com.intellij.util.xmlb.Constants;
import org.jetbrains.annotations.NonNls;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation intended to customize list and set serialization.
 * <p>
 * Two styles are provided:
 * <ul>
 * <li>
 * {@code XCollection.Style.v1}:
 * <pre>{@code
 * <option name="propertyName">
 *   <option value="$value1"/>
 *   <option value="$valueN"/>
 * </option>
 * }</pre>
 * </li>
 * <li>
 * {@code XCollection.Style.v2}:
 * <pre>{@code
 * <propertyName>
 *   <option value="$value1"/>
 *   <option value="$valueN"/>
 * </propertyName>
 * }</pre>
 * </li>
 * </ul>
 *
 * Where second-level {@code option} is an item element (use {@code elementName} to customize element name)
 * and {@code value} is a value attribute (use {@code valueAttributeName} to customize attribute name).
 * <p>
 * Because of backward compatibility, {@code v1} style is used by default. In the examples, {@code v2} style is used.
 * <h3>Custom List Item Value Attribute Name</h3>
 * <p>
 * Value of a primitive type wrapped into an element named {@code option}.
 * The {@code valueAttributeName} attribute allows you to customize the name of the value attribute.
 * <p>
 * Empty name is allowed - in this case, value will be serialized as element text.
 * <ul>
 * <li>
 * {@code valueAttributeName = "customName"}
 * <pre>{@code
 * <propertyName>
 *   <option customName="$value1"/>
 *   <option customName="$valueN"/>
 * </propertyName>
 * }</pre>
 * </li>
 * <li>
 * {@code valueAttributeName = ""}
 * <pre>{@code
 * <propertyName>
 *   <option>$value1</option>
 *   <option>$valueN</option>
 * </propertyName>
 * }</pre>
 * </li>
 * </ul>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.METHOD})
public @interface XCollection {
  /**
   * The property element name. Defaults to property name if {@link #style} is set to {@link Style#v2}.
   * If not specified and {@link #style} is not specified - property serialized using option tag.
   */
  @NonNls String propertyElementName() default "";

  /**
   * Allows customizing the name of the {@code option} element.
   */
  @NonNls String elementName() default Constants.OPTION;

  /**
   * Allows customizing the name of {@code value} attribute of an {@code option} element.
   */
  @NonNls String valueAttributeName() default Constants.VALUE;

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
