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

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.Alarm;
import com.intellij.util.ReflectionUtil;
import org.apache.tools.ant.IntrospectionHelper;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

@Service
final class AntIntrospectorCache implements Disposable {

  private static final Logger LOG = Logger.getInstance(AntIntrospectorCache.class);
  private static final Object ourNullObject = new Object();
  private static final int CACHE_CLEAN_TIMEOUT = 10000; // 10 seconds

  private final HashMap<Class<?>, Object> myCache = new HashMap<>();
  private final Alarm myCacheCleaner = new Alarm(Alarm.ThreadToUse.POOLED_THREAD, this);

  @Override
  public void dispose() {
    synchronized (myCache) {
      myCache.clear();
    }
  }

  @Nullable Object getHelper(Class aClass) {
    Object result;

    synchronized (myCache) {
      result = myCache.get(aClass);
    }
    
    if (result == null) {
      result = ourNullObject;
      Class<?> helperClass = null;
      try {
        final ClassLoader loader = aClass.getClassLoader();
        helperClass = loader != null? loader.loadClass("org.apache.tools.ant.IntrospectionHelper") : IntrospectionHelper.class;
        final Method getHelperMethod = helperClass.getMethod("getHelper", Class.class);
        result = getHelperMethod.invoke(null, aClass);
      }
      catch (ClassNotFoundException | IllegalAccessException | NoSuchMethodException e) {
        LOG.info(e);
      }
      catch (InvocationTargetException ignored) {
      }

      synchronized (myCache) {
        if (helperClass != null) {
          clearAntStaticCache(helperClass);
        }
        myCache.put(aClass, result);
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

  private void scheduleCacheCleaning() {
    myCacheCleaner.cancelAllRequests();
    myCacheCleaner.addRequest(() -> {
      synchronized (myCache) {
        myCache.clear();
      }
    }, CACHE_CLEAN_TIMEOUT);
  }

  static AntIntrospectorCache getInstance() {
    return ApplicationManager.getApplication().getService(AntIntrospectorCache.class);
  }
}
