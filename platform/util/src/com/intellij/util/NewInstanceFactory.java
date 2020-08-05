// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Factory;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Constructor;

public final class NewInstanceFactory<T> implements Factory<T> {
  private static final Logger LOG = Logger.getInstance(NewInstanceFactory.class);

  private final Constructor<? extends T> myConstructor;
  private final Object[] myArgs;

  private NewInstanceFactory(@NotNull Constructor<? extends T> constructor, Object @NotNull [] args) {
    myConstructor = constructor;
    myArgs = args;
  }

  @Override
  public T create() {
    try {
      return myConstructor.newInstance(myArgs);
    }
    catch (Exception e) {
      LOG.error(e);
      return null;
    }
  }

  public static <T> Factory<T> fromClass(@NotNull final Class<? extends T> clazz) {
    try {
      return new NewInstanceFactory<>(clazz.getConstructor(ArrayUtil.EMPTY_CLASS_ARRAY), ArrayUtilRt.EMPTY_OBJECT_ARRAY);
    }
    catch (NoSuchMethodException e) {
      return () -> {
        try {
          return clazz.newInstance();
        }
        catch (Exception e1) {
          LOG.error(e1);
          return null;
        }
      };
    }
  }
}
