// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.xmlb.annotations;

import com.intellij.util.xmlb.Constants;
import com.intellij.util.xmlb.Converter;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * <p>Store value in tag like {@code <option name="optionName" value="optionValue"/>}</p>
 * <p>nameAttribute can be empty, in which case it is skipped: {@code <option value="optionValue" />}</p>
 *
 * @see <a href="https://github.com/JetBrains/intellij-community/tree/master/platform/util/src/com/intellij/util/xmlb/annotations#readme">docs</a>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.METHOD})
public @interface OptionTag {
  String value() default "";

  String tag() default Constants.OPTION;

  String nameAttribute() default Constants.NAME;

  String valueAttribute() default Constants.VALUE;

  Class<? extends Converter> converter() default Converter.class;
}
