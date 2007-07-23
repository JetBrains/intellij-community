/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.lang.ant.psi.impl;

import com.intellij.openapi.diagnostic.Logger;
import org.apache.tools.ant.BuildEvent;
import org.apache.tools.ant.IntrospectionHelper;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;

/**
 * @author Eugene Zhuravlev
 *         Date: Mar 22, 2007
 */
public final class AntInstrospector {
  private static final Logger LOG = Logger.getInstance("#com.intellij.lang.ant.psi.impl.AntInstrospector");
  private final Object myHelper;

  public AntInstrospector(final Class aClass) throws ClassNotFoundException, NoSuchMethodException,
                                               IllegalAccessException, InvocationTargetException {
    final Class<?> helperClass = aClass.getClassLoader().loadClass(IntrospectionHelper.class.getName());
    final Method getHelperMethod = helperClass.getMethod("getHelper", Class.class);
    myHelper = getHelperMethod.invoke(null, aClass);
  }

  public void clearCache() {
    final Class helperClass = myHelper.getClass();
    // for ant 1.7, there is a dedicated method for cache clearing
    try {
      final Method method = helperClass.getDeclaredMethod("clearCache");
      method.invoke(null);
    }
    catch (Throwable e) {
      try {
        final Method method = helperClass.getDeclaredMethod("buildFinished", helperClass.getClassLoader().loadClass(BuildEvent.class.getName()));
        method.invoke(myHelper, new Object[] {null});
      }
      catch (Throwable _e) {
        // ignore. Method is not there since Ant 1.7
      }
    }
    
  }
  
  public static AntInstrospector getInstance(Class c) {
    try {
      return new AntInstrospector(c);
    }
    catch (ClassNotFoundException ignored) {
    }
    catch (NoSuchMethodException ignored) {
    }
    catch (IllegalAccessException ignored) {
    }
    catch (InvocationTargetException e) {
      final Throwable cause = e.getCause();
      if (cause instanceof RuntimeException) {
        throw (RuntimeException)cause;
      }
      if (cause instanceof Error) {
        throw (Error)cause;
      }
    }
    return null;
  }

  private <T> T invokeMethod(@NonNls String methodName, final boolean ignoreErrors, Object... params) {
    final Class helperClass = myHelper.getClass();
    final Class[] types = new Class[params.length];
    try {
      for (int idx = 0; idx < params.length; idx++) {
        types[idx] = params[idx].getClass();
      }
      final Method method = helperClass.getMethod(methodName, types);
      return (T)method.invoke(myHelper, params);
    }
    catch (IllegalAccessException e) {
      if (!ignoreErrors) {
        LOG.error(e);
      }
    }
    catch (NoSuchMethodException e) {
      if (!ignoreErrors) {
        LOG.error(e);
      }
    }
    catch (InvocationTargetException e) {
      final Throwable cause = e.getCause();
      if (cause instanceof RuntimeException) {
        throw (RuntimeException)cause;
      }
      if (cause instanceof Error) {
        throw (Error)cause;
      }
      if (!ignoreErrors) {
        LOG.error(e);
      }
    }
    return null;
  }

  public Set<String> getExtensionPointTypes() {
    final List<Method> methods = invokeMethod("getExtensionPoints", true);
    if (methods == null || methods.size() == 0) {
      return Collections.emptySet();
    }
    final Set<String> types = new HashSet<String>();
    for (Method method : methods) {
      final Class<?>[] paramTypes = method.getParameterTypes();
      for (Class<?> paramType : paramTypes) {
        types.add(paramType.getName());
      }
    }
    return types;
  }
  
  public Enumeration getNestedElements() {
    return invokeMethod("getNestedElements", false);
  }
  
  @Nullable
  public Class getElementType(String name) {
    try {
      return invokeMethod("getElementType", false, name);
    }
    catch (RuntimeException e) {
      return null;
    }
  }

  public Enumeration getAttributes() {
    return invokeMethod("getAttributes", false);
  }

  @Nullable
  public Class getAttributeType(final String attr) {
    try {
      return invokeMethod("getAttributeType", false, attr);
    }
    catch (RuntimeException e) {
      return null;
    }
  }
}
