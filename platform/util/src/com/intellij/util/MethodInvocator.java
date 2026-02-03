// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util;

import com.intellij.openapi.diagnostic.Logger;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public final class MethodInvocator {
  private static final Logger LOG = Logger.getInstance(MethodInvocator.class);

  private Method myMethod;

  public MethodInvocator(Class<?> aClass, String method, Class<?>... parameterTypes) {
    this(true, aClass, method, parameterTypes);
  }

  public MethodInvocator(boolean warnIfAbsent, Class<?> aClass, String method, Class<?>... parameterTypes) {
    try {
      myMethod = aClass.getDeclaredMethod(method, parameterTypes);
      if (!myMethod.isAccessible()) {
        myMethod.setAccessible(true);
      }
    }
    catch (NoSuchMethodException e) {
      if (warnIfAbsent) {
        LOG.warn("Method not found: " + aClass.getName() + "." + method);
      }
    }
  }

  public boolean isAvailable() {
    return myMethod != null;
  }

  public Object invoke(Object object, Object... arguments) {
    if (!isAvailable()) {
      throw new IllegalStateException("Method is not available");
    }

    try {
      return myMethod.invoke(object, arguments);
    }
    catch (IllegalAccessException | InvocationTargetException e) {
      throw new RuntimeException(e);
    }
  }
}
