/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.lang.ant.dom;

import com.intellij.util.xml.ConvertContext;
import com.intellij.util.xml.Converter;
import com.intellij.util.xml.GenericAttributeValue;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

/**
 * @author Eugene Zhuravlev
 */
public class AntBooleanConverter extends Converter<Boolean> {
  public final Boolean DEFAULT_VALUE;

  public AntBooleanConverter() {
    DEFAULT_VALUE = null;
  }

  public AntBooleanConverter(boolean defaultValue) {
    DEFAULT_VALUE = Boolean.valueOf(defaultValue);
  }

  public Boolean fromString(@Nullable @NonNls String s, ConvertContext context) {
    if (s == null || s.length() == 0) {
      return DEFAULT_VALUE;
    }
    return "true".equalsIgnoreCase(s) || "yes".equalsIgnoreCase(s);
  }

  public String toString(@Nullable Boolean aBoolean, ConvertContext context) {
    final GenericAttributeValue attribValue = context.getInvocationElement().getParentOfType(GenericAttributeValue.class, false);
    if (attribValue == null) {
      return null;
    }
    return attribValue.getRawText();
  }
}
