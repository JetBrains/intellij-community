// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.ant;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.ExceptionUtil;
import com.intellij.util.containers.ContainerUtil;
import org.apache.tools.ant.TaskContainer;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;
import java.util.stream.Collectors;

public final class AntIntrospector {
  private static final Logger LOG = Logger.getInstance(AntIntrospector.class);
  private final Object myHelper;
  private final Class myTypeClass;

  private AntIntrospector(final Class aClass, @NotNull Object helper) {
    myTypeClass = aClass;
    myHelper = helper;
  }

  public static @Nullable AntIntrospector getInstance(Class c) {
    Object helper = AntIntrospectorCache.getInstance().getHelper(c);
    return helper == null ? null : new AntIntrospector(c, helper);
  }

  private <T> T invokeMethod(@NonNls String methodName, final boolean ignoreErrors, Object... params) {
    final Class helperClass = myHelper.getClass();
    final Class[] types = new Class[params.length];
    try {
      for (int idx = 0; idx < params.length; idx++) {
        types[idx] = params[idx].getClass();
      }
      return (T)helperClass.getMethod(methodName, types).invoke(myHelper, params);
    }
    catch (IllegalAccessException | NoSuchMethodException e) {
      if (!ignoreErrors) {
        LOG.error(e);
      }
    }
    catch (InvocationTargetException e) {
      final Throwable cause = e.getCause();
      ExceptionUtil.rethrowUnchecked(cause);
      if (!ignoreErrors) {
        LOG.error(e);
      }
    }
    return null;
  }

  public Set<String> getExtensionPointTypes() {
    final List<Method> methods = invokeMethod("getExtensionPoints", true);
    if (ContainerUtil.isEmpty(methods)) {
      return Collections.emptySet();
    }
    return methods.stream().map(Method::getParameterTypes).flatMap(Arrays::stream).map(Class::getName).collect(Collectors.toSet());
  }
  
  public Enumeration<String> getNestedElements() {
    return invokeMethod("getNestedElements", false);
  }
  
  public @Nullable Class getElementType(String name) {
    try {
      return invokeMethod("getElementType", false, name);
    }
    catch (RuntimeException e) {
      return null;
    }
  }

  public Enumeration<String> getAttributes() {
    return invokeMethod("getAttributes", false);
  }

  public @Nullable Class getAttributeType(final String attr) {
    try {
      return invokeMethod("getAttributeType", false, attr);
    }
    catch (RuntimeException e) {
      return null;
    }
  }

  public boolean isContainer() {
    try {
      final Object isContainer = invokeMethod("isContainer", true);
      if (isContainer != null) {
        return Boolean.TRUE.equals(isContainer);
      }
      final ClassLoader loader = myTypeClass.getClassLoader();
      try {
        final Class<?> containerClass = loader != null? loader.loadClass(TaskContainer.class.getName()) : TaskContainer.class;
        return containerClass.isAssignableFrom(myTypeClass);
      }
      catch (ClassNotFoundException ignored) {
      }
    }
    catch (RuntimeException e) {
      LOG.info(e);
    }
    return false;
  }
}
