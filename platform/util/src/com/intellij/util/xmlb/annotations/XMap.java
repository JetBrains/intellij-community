// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.xmlb.annotations;

import com.intellij.util.xmlb.Constants;
import org.jetbrains.annotations.NonNls;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation intended to customize map serialization and to enable a new serialization format.
 * <ul>
 * <li>
 * With {@code XMap} annotation:
 * <pre>{@code
 * <propertyName>
 *   <entry key="key1" value="value1" />
 *   <entry key="keyN" value="valueN" />
 * </propertyName>
 * }</pre>
 * </li>
 * <li>
 * Without {@code XMap} annotation:
 * <pre>{@code
 * <option name="propertyName">
 *   <map>
 *     <entry key="key1" value="value1" />
 *     <entry key="keyN" value="valueN" />
 *   </map>
 * </option>
 * }</pre>
 * </li>
 * </ul>
 * <p>
 * It is recommended to always specify {@code XMap} annotation.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.TYPE, ElementType.METHOD})
public @interface XMap {
  /**
   * The property element name. Defaults to property name.
   */
  @NonNls String propertyElementName() default "";

  @NonNls String entryTagName() default Constants.ENTRY;

  @NonNls String keyAttributeName() default Constants.KEY;

  @NonNls String valueAttributeName() default Constants.VALUE;
}
