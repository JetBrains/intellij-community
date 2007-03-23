/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.lang.ant.psi.impl;

import com.intellij.openapi.diagnostic.Logger;
import org.apache.tools.ant.BuildEvent;
import org.apache.tools.ant.IntrospectionHelper;
import org.jetbrains.annotations.NonNls;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Enumeration;
import java.util.Map;

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

  public void clear() {
    try {
      final Class<? extends Object> helperClass = myHelper.getClass();
      final Method method = helperClass.getDeclaredMethod("buildFinished", helperClass.getClassLoader().loadClass(BuildEvent.class.getName()));
      method.invoke(myHelper, null);
    }
    catch (Throwable e) {
      // ignore. Method is not there since Ant 1.7
    }
  }
  public static AntInstrospector getInstance(Class c) {
    try {
      return new AntInstrospector(c);
    }
    catch (ClassNotFoundException e) {
      LOG.error(e);
    }
    catch (NoSuchMethodException e) {
      LOG.error(e);
    }
    catch (IllegalAccessException e) {
      LOG.error(e);
    }
    catch (InvocationTargetException e) {
      final Throwable cause = e.getCause();
      if (cause instanceof RuntimeException) {
        throw (RuntimeException)cause;
      }
      if (cause instanceof Error) {
        throw (Error)cause;
      }
      LOG.error(e);
    }
    return null;
  }

  private <T> T invokeMethod(@NonNls String methodName, Object... params) {
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
      LOG.error(e);
    }
    catch (NoSuchMethodException e) {
      LOG.error(e);
    }
    catch (InvocationTargetException e) {
      final Throwable cause = e.getCause();
      if (cause instanceof RuntimeException) {
        throw (RuntimeException)cause;
      }
      if (cause instanceof Error) {
        throw (Error)cause;
      }
      LOG.error(e);
    }
    return null;
  }

  public Enumeration getNestedElements() {
    return invokeMethod("getNestedElements");
  }

  public Map getNestedElementMap() {
    return invokeMethod("getNestedElementMap");
  }

  public Enumeration getAttributes() {
    return invokeMethod("getAttributes");
  }

  public Class getAttributeType(final String attr) {
    return invokeMethod("getAttributeType", attr);
  }
}
