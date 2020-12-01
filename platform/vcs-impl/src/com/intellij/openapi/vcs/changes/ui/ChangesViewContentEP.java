// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.ui;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.PluginAware;
import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.openapi.extensions.ProjectExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.util.NotNullFunction;
import com.intellij.util.pico.CachingConstructorInjectionComponentAdapter;
import com.intellij.util.xmlb.annotations.Attribute;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

/**
 * @author yole
 */
public class ChangesViewContentEP implements PluginAware {
  private static final Logger LOG = Logger.getInstance(ChangesViewContentEP.class);

  public static final ProjectExtensionPointName<ChangesViewContentEP> EP_NAME = new ProjectExtensionPointName<>("com.intellij.changesViewContent");

  /**
   * Used to determine specific tab content in {@link ChangesViewContentManager#selectContent}
   * <p>
   * To provide localized tab name use {@link #displayNameSupplierClassName}
   */
  @Attribute("tabName")
  public String tabName;

  @Attribute("className")
  public String className;

  @Attribute("predicateClassName")
  public String predicateClassName;

  @Attribute("preloaderClassName")
  public String preloaderClassName;

  @Attribute("displayNameSupplierClassName")
  public String displayNameSupplierClassName;

  @Attribute("isInCommitToolWindow")
  public boolean isInCommitToolWindow;

  private PluginDescriptor myPluginDescriptor;
  private ChangesViewContentProvider myInstance;

  @Override
  public void setPluginDescriptor(@NotNull PluginDescriptor pluginDescriptor) {
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

  public String getPreloaderClassName() {
    return preloaderClassName;
  }

  public void setPreloaderClassName(final String preloaderClassName) {
    this.preloaderClassName = preloaderClassName;
  }

  public String getDisplayNameSupplierClassName() {
    return displayNameSupplierClassName;
  }

  public void setDisplayNameSupplierClassName(String displayNameSupplierClassName) {
    this.displayNameSupplierClassName = displayNameSupplierClassName;
  }

  public boolean isInCommitToolWindow() {
    return isInCommitToolWindow;
  }

  public void setInCommitToolWindow(boolean isInCommitToolWindow) {
    this.isInCommitToolWindow = isInCommitToolWindow;
  }

  public ChangesViewContentProvider getInstance(@NotNull Project project) {
    if (myInstance == null) {
      myInstance = (ChangesViewContentProvider)newClassInstance(project, className);
    }
    return myInstance;
  }

  @Nullable
  public ChangesViewContentProvider getCachedInstance() {
    return myInstance;
  }

  @Nullable
  public NotNullFunction<Project, Boolean> newPredicateInstance(@NotNull Project project) {
    if (predicateClassName == null) {
      return null;
    }
    //noinspection unchecked
    return (NotNullFunction<Project, Boolean>)newClassInstance(project, predicateClassName);
  }

  @Nullable
  public ChangesViewContentProvider.Preloader newPreloaderInstance(@NotNull Project project) {
    if (preloaderClassName == null) {
      return null;
    }
    return (ChangesViewContentProvider.Preloader)newClassInstance(project, preloaderClassName);
  }

  @Nullable
  public Supplier<String> newDisplayNameSupplierInstance(@NotNull Project project) {
    if (displayNameSupplierClassName == null) {
      return null;
    }
    //noinspection unchecked
    return (Supplier<String>)newClassInstance(project, displayNameSupplierClassName);
  }

  @Nullable
  private Object newClassInstance(@NotNull Project project, @NotNull String className) {
    try {
      Class<?> aClass = Class.forName(className, true,
                                      myPluginDescriptor == null ? getClass().getClassLoader() : myPluginDescriptor.getPluginClassLoader());
      return new CachingConstructorInjectionComponentAdapter(className, aClass).getComponentInstance(project.getPicoContainer());
    }
    catch (Exception e) {
      LOG.error(e);
      return null;
    }
  }
}
