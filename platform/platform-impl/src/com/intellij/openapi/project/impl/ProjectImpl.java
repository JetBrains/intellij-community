// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.project.impl;

import com.intellij.configurationStore.StoreUtil;
import com.intellij.ide.plugins.ContainerDescriptor;
import com.intellij.ide.plugins.IdeaPluginDescriptorImpl;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.client.ClientAwareComponentManager;
import com.intellij.openapi.components.impl.stores.IProjectStore;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.ex.ProjectEx;
import com.intellij.openapi.project.ex.ProjectManagerEx;
import com.intellij.openapi.util.Key;
import com.intellij.serviceContainer.ComponentManagerImpl;
import com.intellij.util.ExceptionUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

public abstract class ProjectImpl extends ClientAwareComponentManager implements ProjectEx {
  protected static final Logger LOG = Logger.getInstance(ProjectImpl.class);

  public static final Key<Long> CREATION_TIME = Key.create("ProjectImpl.CREATION_TIME");

  @TestOnly
  @NonNls
  public static final String LIGHT_PROJECT_NAME = "light_temp";

  static boolean ourClassesAreLoaded;
  private static final Key<String> CREATION_TRACE = Key.create("ProjectImpl.CREATION_TRACE");

  protected ProjectImpl(@NotNull ComponentManagerImpl parent) {
    super(parent);
    storeCreationTrace();
  }

  @Override
  public boolean isOpen() {
    ProjectManagerEx projectManager = ProjectManagerEx.getInstanceExIfCreated();
    return projectManager != null && projectManager.isProjectOpened(this);
  }

  @Override
  protected @NotNull ContainerDescriptor getContainerDescriptor(@NotNull IdeaPluginDescriptorImpl pluginDescriptor) {
    return pluginDescriptor.projectContainerDescriptor;
  }

  protected abstract IProjectStore getStateStore();

  public void init(boolean preloadServices, @Nullable ProgressIndicator indicator) {
  }

  @Override
  protected void setProgressDuringInit(@NotNull ProgressIndicator indicator) {
    indicator.setFraction(getPercentageOfComponentsLoaded() / (ourClassesAreLoaded ? 10 : 2));
  }

  @Override
  public void save() {
    if (!ApplicationManagerEx.getApplicationEx().isSaveAllowed()) {
      // no need to save
      return;
    }

    // ensure that expensive save operation is not performed before startupActivityPassed
    // first save may be quite cost operation, because cache is not warmed up yet
    if (!isInitialized()) {
      LOG.debug("Skip save for " + getName() + ": not initialized");
      return;
    }

    StoreUtil.saveSettings(this, false);
  }

  @Override
  @TestOnly
  public String getCreationTrace() {
    return getUserData(CREATION_TRACE);
  }

  void storeCreationTrace() {
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      putUserData(CREATION_TRACE, ExceptionUtil.currentStackTrace());
    }
  }
}
