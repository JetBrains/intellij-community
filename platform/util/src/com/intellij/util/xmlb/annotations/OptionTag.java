/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
 * @author nik
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
