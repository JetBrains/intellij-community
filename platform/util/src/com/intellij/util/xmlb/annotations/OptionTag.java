// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.xmlb.annotations;

import com.intellij.util.xmlb.Constants;
import com.intellij.util.xmlb.Converter;
import org.jetbrains.annotations.NonNls;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * <p>
 * Store value in tag like {@code <option name="optionName" value="optionValue"/>}.
 * <p>
 * {@code nameAttribute} can be empty, in which case it is skipped: {@code <option value="optionValue"/>}
 *
 * @see XCollection
 * @see XMap
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.METHOD})
public @interface OptionTag {
  @NonNls String value() default "";

  @NonNls String tag() default Constants.OPTION;

  @NonNls String nameAttribute() default Constants.NAME;

  @NonNls String valueAttribute() default Constants.VALUE;

  Class<? extends Converter> converter() default Converter.class;
}
