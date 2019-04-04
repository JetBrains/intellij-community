// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.xmlb;

import com.intellij.util.ReflectionUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class XmlSerializerUtil {
  private XmlSerializerUtil() {
  }

  public static <T> void copyBean(@NotNull T from, @NotNull T to) {
    assert from.getClass().isAssignableFrom(to.getClass()) : "Beans of different classes specified: Cannot assign " +
                                                             from.getClass() + " to " + to.getClass();
    for (MutableAccessor accessor : BeanBinding.getAccessors(from.getClass())) {
      accessor.set(to, accessor.read(from));
    }
  }

  public static <T> T createCopy(@NotNull T from) {
    try {
      @SuppressWarnings("unchecked")
      T to = (T)ReflectionUtil.newInstance(from.getClass());
      copyBean(from, to);
      return to;
    }
    catch (Exception ignored) {
      return null;
    }
  }

  @NotNull
  public static List<MutableAccessor> getAccessors(@NotNull Class<?> aClass) {
    return BeanBinding.getAccessors(aClass);
  }

  @Nullable
  public static Object stringToEnum(@NotNull String value, @NotNull Class<? extends Enum<?>> valueClass, boolean isAlwaysIgnoreCase) {
    Enum<?>[] enumConstants = valueClass.getEnumConstants();
    if (!isAlwaysIgnoreCase) {
      for (Object enumConstant : enumConstants) {
        if (enumConstant.toString().equals(value)) {
          return enumConstant;
        }
      }
    }
    for (Object enumConstant : enumConstants) {
      if (enumConstant.toString().equalsIgnoreCase(value)) {
        return enumConstant;
      }
    }
    return null;
  }
}
