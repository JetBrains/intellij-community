/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.util.Pair;
import com.intellij.util.ExceptionUtil;
import com.intellij.util.ReflectionUtil;
import org.jetbrains.annotations.Nullable;

import java.lang.ref.SoftReference;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

/**
* @author Eugene Zhuravlev
*/
public final class ReflectedProject {
  private static final Logger LOG = Logger.getInstance("#com.intellij.lang.ant.ReflectedProject");


  private static final List<SoftReference<Pair<ReflectedProject, ClassLoader>>> ourProjects =
    new ArrayList<>();

  private static final ReentrantLock ourProjectsLock = new ReentrantLock();
  
  private final Object myProject;
  private Hashtable myTaskDefinitions;
  private Hashtable myDataTypeDefinitions;
  private Hashtable myProperties;
  private Class myTargetClass;

  public static ReflectedProject getProject(final ClassLoader classLoader) {
    ourProjectsLock.lock();
    try {
      for (Iterator<SoftReference<Pair<ReflectedProject, ClassLoader>>> iterator = ourProjects.iterator(); iterator.hasNext();) {
        final SoftReference<Pair<ReflectedProject, ClassLoader>> ref = iterator.next();
        final Pair<ReflectedProject, ClassLoader> pair = ref.get();
        if (pair == null) {
          iterator.remove();
        }
        else {
          if (pair.second == classLoader) {
            return pair.first;
          }
        }
      }
    }
    finally {
      ourProjectsLock.unlock();
    }
    final ReflectedProject reflectedProj = new ReflectedProject(classLoader);
    ourProjectsLock.lock();
    try {
      ourProjects.add(new SoftReference<>(
        Pair.create(reflectedProj, classLoader)
      ));
    }
    finally {
      ourProjectsLock.unlock();
    }
    return reflectedProj;
  }

  ReflectedProject(final ClassLoader classLoader) {
    Object project = null;
    try {
      final Class projectClass = classLoader.loadClass("org.apache.tools.ant.Project");
      if (projectClass != null) {
        project = projectClass.newInstance();
        Method method = projectClass.getMethod("init");
        method.invoke(project);
        method = ReflectionUtil.getMethod(projectClass, "getTaskDefinitions");
        myTaskDefinitions = (Hashtable)method.invoke(project);
        method = ReflectionUtil.getMethod(projectClass, "getDataTypeDefinitions");
        myDataTypeDefinitions = (Hashtable)method.invoke(project);
        method = ReflectionUtil.getMethod(projectClass, "getProperties");
        myProperties = (Hashtable)method.invoke(project);
        myTargetClass = classLoader.loadClass("org.apache.tools.ant.Target");
      }
    }
    catch (ProcessCanceledException e) {
      throw e;
    }
    catch (Throwable e) {
      // rethrow PCE if it was the cause
      final Throwable cause = ExceptionUtil.getRootCause(e);
      if (cause instanceof ProcessCanceledException) {
        throw (ProcessCanceledException)cause;
      }
      LOG.info(e);
      project = null;
    }
    myProject = project;
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

  @Nullable
  public Object getProject() {
    return myProject;
  }
}
