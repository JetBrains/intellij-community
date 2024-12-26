// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.ant;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.util.Pair;
import com.intellij.util.ExceptionUtil;
import org.jetbrains.annotations.Nullable;

import java.lang.ref.SoftReference;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.locks.ReentrantLock;

/**
* @author Eugene Zhuravlev
*/
public final class ReflectedProject {
  private static final Logger LOG = Logger.getInstance(ReflectedProject.class);

  private static final List<SoftReference<Pair<ReflectedProject, ClassLoader>>> ourProjects =
    new ArrayList<>();

  private static final ReentrantLock ourProjectsLock = new ReentrantLock();
  public static final String ANT_PROJECT_CLASS = "org.apache.tools.ant.Project";
  public static final String COMPONENT_HELPER_CLASS = "org.apache.tools.ant.ComponentHelper";

  private final Object myProject;
  private Map<String, Class<?>> myTaskDefinitions;
  private Map<String, Class<?>> myDataTypeDefinitions;
  private Map<String, Collection<Class<?>>> myRestrictedDefinitions;
  private Map<String, String> myProperties;
  private Class<?> myTargetClass;

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
    Object project;
    try {
      final Class<?> projectClass = classLoader.loadClass(ANT_PROJECT_CLASS);
      project = projectClass.getConstructor().newInstance();
      Method method = projectClass.getMethod("init");
      method.invoke(project);
      method = projectClass.getMethod("getTaskDefinitions");
      //noinspection unchecked
      myTaskDefinitions = (Map<String, Class<?>>)method.invoke(project);
      method = projectClass.getMethod( "getDataTypeDefinitions");
      //noinspection unchecked
      myDataTypeDefinitions = (Map<String, Class<?>>)method.invoke(project);
      method = projectClass.getMethod( "getProperties");
      //noinspection unchecked
      myProperties = (Map<String, String>)method.invoke(project);
      myTargetClass = classLoader.loadClass("org.apache.tools.ant.Target");

      try {
        myRestrictedDefinitions = new HashMap<>();
        final Class<?> componentHelperClass = classLoader.loadClass(COMPONENT_HELPER_CLASS);
        final Object helper = componentHelperClass.getMethod("getComponentHelper", projectClass).invoke(null, project);
        componentHelperClass.getMethod("getDefinition", String.class).invoke(helper, "ant"); // this will initialize restricted definitions
        final Method getRestrictedDef = componentHelperClass.getDeclaredMethod("getRestrictedDefinition");
        getRestrictedDef.setAccessible(true);
        final Map<String, ? extends Collection<?>> restrictedDefinitions = (Map<String, ? extends Collection<?>>)getRestrictedDef.invoke(helper);
        for (Map.Entry<String, ? extends Collection<?>> entry : restrictedDefinitions.entrySet()) {
          final List<Class<?>> classes = new ArrayList<>();
          for (Object /* org.apache.tools.ant.AntTypeDefinition */ typeDef : entry.getValue()) {
            try {
              classes.add((Class<?>)typeDef.getClass().getMethod("getTypeClass", projectClass).invoke(typeDef, project));
            }
            catch (Throwable ignored) {
            }
          }
          myRestrictedDefinitions.put(entry.getKey(), classes);
        }
      }
      catch (Throwable ignored) {
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

  public @Nullable Map<String, Class<?>> getTaskDefinitions() {
    return myTaskDefinitions;
  }

  public @Nullable Map<String, Class<?>> getDataTypeDefinitions() {
    return myDataTypeDefinitions;
  }

  public Map<String, Collection<Class<?>>> getRestrictedDefinitions() {
    return myRestrictedDefinitions;
  }

  public Map<String, String> getProperties() {
    return myProperties;
  }

  public Class<?> getTargetClass() {
    return myTargetClass;
  }

  public @Nullable Object getProject() {
    return myProject;
  }
}
