/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.lang.ant.psi.impl;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.StringBuilderSpinAllocator;
import com.intellij.util.containers.ObjectCache;
import org.apache.tools.ant.BuildEvent;
import org.apache.tools.ant.IntrospectionHelper;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import java.lang.ref.SoftReference;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;

/**
 * @author Eugene Zhuravlev
 *         Date: Mar 22, 2007
 */
public final class AntIntrospector {
  private static final Logger LOG = Logger.getInstance("#com.intellij.lang.ant.psi.impl.AntIntrospector");
  private final Object myHelper;
  private static final ObjectCache<String, SoftReference<Object>> ourCache = new ObjectCache<String, SoftReference<Object>>(); 
  
  public AntIntrospector(final Class aClass) throws ClassNotFoundException, NoSuchMethodException,
                                               IllegalAccessException, InvocationTargetException {
    myHelper = getHelper(aClass);
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
  
  public static AntIntrospector getInstance(Class c) {
    try {
      return new AntIntrospector(c);
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
  
  // for debug purposes
  //private static int ourAttempts = 0;
  //private static int ourHits = 0;
  @Nullable
  private static Object getHelper(Class aClass) throws ClassNotFoundException, NoSuchMethodException, InvocationTargetException, IllegalAccessException {
    final String key;
    final ClassLoader loader = aClass.getClassLoader();
    final StringBuilder builder = StringBuilderSpinAllocator.alloc();
    try {
      builder.append(aClass.getName());
      if (loader != null) {
        builder.append("_");
        builder.append(loader.hashCode());
      }
      key = builder.toString();
    }
    finally {
      StringBuilderSpinAllocator.dispose(builder);
    }
    
    Object result = null;
    
    synchronized (ourCache) {
      //++ourAttempts;
      final SoftReference<Object> ref = ourCache.tryKey(key);
      result = (ref == null) ? null : ref.get();
      if (result == null && ref != null) {
        ourCache.remove(key);
      }
      // for debug purposes
      //if (result != null) {
      //  ++ourHits;
      //  final double hitRate = (ourAttempts > 0) ? ((double)ourHits / (double)ourAttempts) : 0;
      //  System.out.println("cache hit! (" + key + ") "  + hitRate);
      //}
    }
    
    if (result == null) {
      final Class<?> helperClass = loader != null? loader.loadClass(IntrospectionHelper.class.getName()) : IntrospectionHelper.class;
      final Method getHelperMethod = helperClass.getMethod("getHelper", Class.class);
      result = getHelperMethod.invoke(null, aClass);
    }
    
    if (result != null) {
      synchronized (ourCache) {
        ourCache.cacheObject(key, new SoftReference<Object>(result));
      }
    }
    
    return result;
  }
  
}
