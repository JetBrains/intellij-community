/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.lang.ant.psi.impl;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.util.Pair;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import java.lang.ref.SoftReference;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;

/**
* @author Eugene Zhuravlev
*         Date: Apr 9, 2010
*/
public final class ReflectedProject {
  private static final Logger LOG = Logger.getInstance("#com.intellij.lang.ant.psi.impl.ReflectedProject");
  
  @NonNls private static final String INIT_METHOD_NAME = "init";
  @NonNls private static final String GET_TASK_DEFINITIONS_METHOD_NAME = "getTaskDefinitions";
  @NonNls private static final String GET_DATA_TYPE_DEFINITIONS_METHOD_NAME = "getDataTypeDefinitions";
  @NonNls private static final String GET_PROPERTIES_METHOD_NAME = "getProperties";


  private static final List<SoftReference<Pair<ReflectedProject, ClassLoader>>> ourProjects =
    new ArrayList<SoftReference<Pair<ReflectedProject, ClassLoader>>>();

  private final Object myProject;
  private Hashtable myTaskDefinitions;
  private Hashtable myDataTypeDefinitions;
  private Hashtable myProperties;
  private Class myTargetClass;

  public static ReflectedProject getProject(final ClassLoader classLoader) {
    for (Iterator<SoftReference<Pair<ReflectedProject, ClassLoader>>> iterator = ourProjects.iterator(); iterator.hasNext();) {
      final SoftReference<Pair<ReflectedProject, ClassLoader>> ref = iterator.next();
      final Pair<ReflectedProject, ClassLoader> pair = ref.get();
      if (pair == null) {
        iterator.remove();
      }
      else {
        if (pair.first != null && pair.first.getProject() == null) {
          iterator.remove();
        }
        if (pair.second == classLoader) {
          return pair.first;
        }
      }
    }
    final ReflectedProject project = new ReflectedProject(classLoader);
    ourProjects.add(new SoftReference<Pair<ReflectedProject, ClassLoader>>(
      new Pair<ReflectedProject, ClassLoader>(project, classLoader)
    ));
    return project;
  }

  ReflectedProject(final ClassLoader classLoader) {
    Object myProject = null;
    try {
      final Class projectClass = classLoader.loadClass("org.apache.tools.ant.Project");
      if (projectClass != null) {
        myProject = projectClass.newInstance();
        Method method = projectClass.getMethod(INIT_METHOD_NAME);
        method.invoke(myProject);
        method = getMethod(projectClass, GET_TASK_DEFINITIONS_METHOD_NAME);
        myTaskDefinitions = (Hashtable)method.invoke(myProject);
        method = getMethod(projectClass, GET_DATA_TYPE_DEFINITIONS_METHOD_NAME);
        myDataTypeDefinitions = (Hashtable)method.invoke(myProject);
        method = getMethod(projectClass, GET_PROPERTIES_METHOD_NAME);
        myProperties = (Hashtable)method.invoke(myProject);
        myTargetClass = classLoader.loadClass("org.apache.tools.ant.Target");
      }
    }
    catch (ProcessCanceledException e) {
      throw e;
    }
    catch (ExceptionInInitializerError e) {
      final Throwable cause = e.getCause();
      if (cause instanceof ProcessCanceledException) {
        throw (ProcessCanceledException)cause;
      }
      else {
        LOG.info(e);
        myProject = null;
      }
    }
    catch (InvocationTargetException e) {
      final Throwable cause = e.getCause();
      if (cause instanceof ProcessCanceledException) {
        throw (ProcessCanceledException)cause;
      }
      else {
        LOG.info(e);
        myProject = null;
      }
    }
    catch (Exception e) {
      LOG.info(e);
      myProject = null;
    }
    this.myProject = myProject;
  }

  private static Method getMethod(final Class introspectionHelperClass, final String name) throws NoSuchMethodException {
    final Method method;
    method = introspectionHelperClass.getMethod(name);
    if (!method.isAccessible()) {
      method.setAccessible(true);
    }
    return method;
  }

  @Nullable
  public Hashtable<String, Class> getTaskDefinitions() {
    return myTaskDefinitions;
  }

  @Nullable
  public Hashtable<String, Class> getDataTypeDefinitions() {
    return myDataTypeDefinitions;
  }

  public Hashtable getProperties() {
    return myProperties;
  }

  public Class getTargetClass() {
    return myTargetClass;
  }

  public Object getProject() {
    return myProject;
  }
}
