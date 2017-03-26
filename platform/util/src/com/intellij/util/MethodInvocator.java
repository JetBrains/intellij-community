/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.util;

import com.intellij.openapi.diagnostic.Logger;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public class MethodInvocator {
  private static final Logger LOG = Logger.getInstance("#com.intellij.util.MethodInvocator");

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
    catch (IllegalAccessException e) {
      throw new RuntimeException(e);
    }
    catch (InvocationTargetException e) {
      throw new RuntimeException(e);
    }
  }
}
