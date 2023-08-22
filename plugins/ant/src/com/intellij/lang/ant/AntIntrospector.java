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
package com.intellij.lang.ant;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.Alarm;
import com.intellij.util.ExceptionUtil;
import com.intellij.util.ReflectionUtil;
import com.intellij.util.containers.ContainerUtil;
import org.apache.tools.ant.IntrospectionHelper;
import org.apache.tools.ant.TaskContainer;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;
import java.util.stream.Collectors;

public final class AntIntrospector {
  private static final Logger LOG = Logger.getInstance(AntIntrospector.class);
  private final Object myHelper;
  private static final HashMap<Class, Object> ourCache = new HashMap<>();
  private static final Object ourNullObject = new Object();
  private static final Alarm ourCacheCleaner = new Alarm(Alarm.ThreadToUse.POOLED_THREAD, AntDisposable.getInstance());
  private static final int CACHE_CLEAN_TIMEOUT = 10000; // 10 seconds
  private final Class myTypeClass;

  private AntIntrospector(final Class aClass) {
    myTypeClass = aClass;
    myHelper = getHelper(aClass);
  }

  @Nullable
  public static AntIntrospector getInstance(Class c) {
    final AntIntrospector antIntrospector = new AntIntrospector(c);
    return antIntrospector.myHelper == null? null : antIntrospector;
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
  
  @Nullable
  public Class getElementType(String name) {
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

  @Nullable
  public Class getAttributeType(final String attr) {
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

  @Nullable
  private static Object getHelper(final Class aClass) {
    Object result = null;

    synchronized (ourCache) {
      result = ourCache.get(aClass);
    }
    
    if (result == null) {
      result = ourNullObject;
      Class<?> helperClass = null;
      try {
        final ClassLoader loader = aClass.getClassLoader();
        helperClass = loader != null? loader.loadClass(IntrospectionHelper.class.getName()) : IntrospectionHelper.class;
        final Method getHelperMethod = helperClass.getMethod("getHelper", Class.class);
        result = getHelperMethod.invoke(null, aClass);
      }
      catch (ClassNotFoundException | IllegalAccessException | NoSuchMethodException e) {
        LOG.info(e);
      }
      catch (InvocationTargetException ignored) {
      }

      synchronized (ourCache) {
        if (helperClass != null) {
          clearAntStaticCache(helperClass);
        }
        ourCache.put(aClass, result);
      }
    }
    scheduleCacheCleaning();
    return result == ourNullObject? null : result;
  }
  
  private static void clearAntStaticCache(final Class<?> helperClass) {
    // for ant 1.7, there is a dedicated method for cache clearing
    try {
      helperClass.getDeclaredMethod("clearCache").invoke(null);
    }
    catch (Throwable e) {
      try {
        // assume it is older version of ant
        Map helpersCollection = ReflectionUtil.getField(helperClass, null, null, "helpers");
        if (helpersCollection != null) {
          helpersCollection.clear();
        }
      }
      catch (Throwable _e) {
        // ignore.
      }
    }
  }

  private static void scheduleCacheCleaning() {
    ourCacheCleaner.cancelAllRequests();
    ourCacheCleaner.addRequest(() -> {
      synchronized (ourCache) {
        ourCache.clear();
      }
    }, CACHE_CLEAN_TIMEOUT);
  }

}
