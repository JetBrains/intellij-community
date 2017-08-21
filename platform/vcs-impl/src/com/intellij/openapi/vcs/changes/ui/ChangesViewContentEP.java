/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.openapi.vcs.changes.ui;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.extensions.PluginAware;
import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.util.NotNullFunction;
import com.intellij.util.pico.CachingConstructorInjectionComponentAdapter;
import com.intellij.util.xmlb.annotations.Attribute;
import org.jetbrains.annotations.Nullable;

/**
 * @author yole
 */
public class ChangesViewContentEP implements PluginAware {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.vcs.changes.ui.ChangesViewContentEP");

  public static final ExtensionPointName<ChangesViewContentEP> EP_NAME = new ExtensionPointName<>("com.intellij.changesViewContent");

  @Attribute("tabName")
  public String tabName;

  @Attribute("className")
  public String className;

  @Attribute("predicateClassName")
  public String predicateClassName;

  private PluginDescriptor myPluginDescriptor;
  private ChangesViewContentProvider myInstance;

  public void setPluginDescriptor(PluginDescriptor pluginDescriptor) {
    myPluginDescriptor = pluginDescriptor;
  }

  public String getTabName() {
    return tabName;
  }

  public void setTabName(final String tabName) {
    this.tabName = tabName;
  }

  public String getClassName() {
    return className;
  }

  public void setClassName(final String className) {
    this.className = className;
  }

  public String getPredicateClassName() {
    return predicateClassName;
  }

  public void setPredicateClassName(final String predicateClassName) {
    this.predicateClassName = predicateClassName;
  }

  public ChangesViewContentProvider getInstance(Project project) {
    if (myInstance == null) {
      myInstance = (ChangesViewContentProvider) newClassInstance(project, className);
    }
    return myInstance;
  }

  @Nullable
  public ChangesViewContentProvider getCachedInstance() {
    return myInstance;
  }

  @Nullable
  public NotNullFunction<Project, Boolean> newPredicateInstance(Project project) {
    //noinspection unchecked
    return predicateClassName != null ? (NotNullFunction<Project, Boolean>)newClassInstance(project, predicateClassName) : null;
  }

  private Object newClassInstance(final Project project, final String className) {
    try {
      final Class<?> aClass = Class.forName(className, true,
                                            myPluginDescriptor == null ? getClass().getClassLoader()  : myPluginDescriptor.getPluginClassLoader());
      return new CachingConstructorInjectionComponentAdapter(className, aClass).getComponentInstance(project.getPicoContainer());
    }
    catch(Exception e) {
      LOG.error(e);
      return null;
    }
  }
}
