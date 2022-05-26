// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.ui;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.PluginAware;
import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.openapi.extensions.ProjectExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.NlsContexts.TabTitle;
import com.intellij.ui.content.Content;
import com.intellij.util.NotNullFunction;
import com.intellij.util.xmlb.annotations.Attribute;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Extension point to register persistent tabs in 'Version Control' and 'Commit' toolwindows.
 * <p>
 * Tabs are initialized lazily and {@link ChangesViewContentProvider#initTabContent} is only called when tab is selected for the first time.
 * Use {@link #preloaderClassName} to perform activity on tab creation, even if it is not selected yet.
 * <p>
 * Classes specified in attributes may receive {@link Project} in constructor parameter.
 */
public final class ChangesViewContentEP implements PluginAware {
  private static final Logger LOG = Logger.getInstance(ChangesViewContentEP.class);

  public static final ProjectExtensionPointName<ChangesViewContentEP> EP_NAME =
    new ProjectExtensionPointName<>("com.intellij.changesViewContent");

  /**
   * Non-user-visible id that is used to locate specific tab content in {@link ChangesViewContentManager#selectContent} and similar methods.
   * <p>
   * To provide localized tab name use {@link #displayNameSupplierClassName}
   *
   * @see Content#getTabName()
   */
  @Attribute("tabName")
  public String tabName;

  /**
   * {@link ChangesViewContentProvider} instance that is used to set tab content.
   */
  @Attribute("className")
  public String className;

  /**
   * Optional {@link Predicate<Project>} instance that is being used to check if tab should be visible.
   * <p>
   * Use {@link ChangesViewContentManagerListener#TOPIC} to notify that predicate value might have changed.
   */
  @Attribute("predicateClassName")
  public String predicateClassName;

  /**
   * Optional {@link ChangesViewContentProvider.Preloader} instance that invoked on {@link com.intellij.ui.content.Content} creation.
   * <p>
   * ex: it can be used to register DnD-drop handlers.
   * It can be also used to specify tab order, see {@link ChangesViewContentManager#ORDER_WEIGHT_KEY}.
   */
  @Attribute("preloaderClassName")
  public String preloaderClassName;

  /**
   * {@link Supplier<@NlsContexts.TabTitle String>} instance that returns user-visible title.
   *
   * @see Content#getDisplayName()
   */
  @Attribute("displayNameSupplierClassName")
  public String displayNameSupplierClassName;

  /**
   * Whether tab should be shown in 'Version Control' toolwindow (default) or in 'Commit' toolwindow.
   * Note, that 'Commit' toolwindow may be disabled, see {@link ChangesViewContentManager#isCommitToolWindowShown(Project)}.
   * <p>
   * Use {@link ChangesViewContentManager#getToolWindowFor(Project, String)} to get actual toolwindow for the tab.
   */
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

  public @TabTitle @Nullable String getDisplayName(@NotNull Project project) {
    Supplier<String> supplier = newDisplayNameSupplierInstance(project);
    return supplier != null ? supplier.get() : null; //NON-NLS
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

  public @Nullable Predicate<Project> newPredicateInstance(@NotNull Project project) {
    if (predicateClassName == null) {
      return null;
    }

    Object predicate = newClassInstance(project, predicateClassName);
    if (predicate == null) {
      return null;
    }
    else if (predicate instanceof Predicate) {
      //noinspection unchecked
      return (Predicate<Project>)predicate;
    }
    else {
      //noinspection unchecked
      NotNullFunction<Project, Boolean> oldPredicate = (NotNullFunction<Project, Boolean>)predicate;
      return it -> oldPredicate.fun(it) == Boolean.TRUE;
    }
  }

  @Nullable
  public ChangesViewContentProvider.Preloader newPreloaderInstance(@NotNull Project project) {
    if (preloaderClassName == null) {
      return null;
    }
    return (ChangesViewContentProvider.Preloader)newClassInstance(project, preloaderClassName);
  }

  private @Nullable Supplier<String> newDisplayNameSupplierInstance(@NotNull Project project) {
    if (displayNameSupplierClassName == null) {
      return null;
    }
    //noinspection unchecked
    return (Supplier<String>)newClassInstance(project, displayNameSupplierClassName);
  }

  private @Nullable Object newClassInstance(@NotNull Project project, @NotNull String className) {
    try {
      return project.instantiateClass(className, myPluginDescriptor);
    }
    catch (Exception e) {
      LOG.error(e);
      return null;
    }
  }
}
