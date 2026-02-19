// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.ant.dom;

import com.intellij.util.xml.ConvertContext;
import com.intellij.util.xml.Converter;
import com.intellij.util.xml.GenericAttributeValue;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
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

  @Override
  public Boolean fromString(@Nullable @NonNls String s, @NotNull ConvertContext context) {
    if (s == null || s.isEmpty()) {
      return DEFAULT_VALUE;
    }
    return "true".equalsIgnoreCase(s) || "yes".equalsIgnoreCase(s);
  }

  @Override
  public String toString(@Nullable Boolean aBoolean, @NotNull ConvertContext context) {
    final GenericAttributeValue attribValue = context.getInvocationElement().getParentOfType(GenericAttributeValue.class, false);
    if (attribValue == null) {
      return null;
    }
    return attribValue.getRawText();
  }
}
