// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.project.impl;

import com.intellij.configurationStore.StoreUtil;
import com.intellij.ide.plugins.ContainerDescriptor;
import com.intellij.ide.plugins.IdeaPluginDescriptorImpl;
import com.intellij.ide.startup.StartupManagerEx;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.components.impl.stores.IProjectStore;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.ex.ProjectEx;
import com.intellij.openapi.project.ex.ProjectManagerEx;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.openapi.wm.impl.FrameTitleBuilder;
import com.intellij.serviceContainer.ComponentManagerImpl;
import com.intellij.util.ExceptionUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import javax.swing.*;

public abstract class ProjectImpl extends ComponentManagerImpl implements ProjectEx {
  protected static final Logger LOG = Logger.getInstance(ProjectImpl.class);

  public static final Key<Long> CREATION_TIME = Key.create("ProjectImpl.CREATION_TIME");

  @TestOnly
  public static final String LIGHT_PROJECT_NAME = "light_temp";

  protected String myName;
  static boolean ourClassesAreLoaded;
  private final String creationTrace = ApplicationManager.getApplication().isUnitTestMode() ? ExceptionUtil.currentStackTrace() : null;

  protected ProjectImpl(@NotNull ComponentManagerImpl parent) {
    super(parent);
  }

  @Override
  public void setProjectName(@NotNull String projectName) {
    if (projectName.equals(myName)) {
      return;
    }

    myName = projectName;

    if (!ApplicationManager.getApplication().isUnitTestMode()) {
      StartupManager.getInstance(this).runAfterOpened(() -> {
        ApplicationManager.getApplication().invokeLater(() -> {
          JFrame frame = WindowManager.getInstance().getFrame(this);
          String title = FrameTitleBuilder.getInstance().getProjectTitle(this);
          if (frame != null && title != null) {
            frame.setTitle(title);
          }
        }, ModalityState.NON_MODAL, getDisposed());
      });
    }
  }

  @Override
  public boolean isOpen() {
    ProjectManagerEx projectManager = ProjectManagerEx.getInstanceExIfCreated();
    return projectManager != null && projectManager.isProjectOpened(this);
  }

  @Override
  public boolean isInitialized() {
    return getComponentCreated() && !isDisposed() && isOpen() && StartupManagerEx.getInstanceEx(this).startupActivityPassed();
  }

  @Override
  protected @NotNull ContainerDescriptor getContainerDescriptor(@NotNull IdeaPluginDescriptorImpl pluginDescriptor) {
    return pluginDescriptor.getProject();
  }

  @Override
  public @NotNull String getName() {
    if (myName == null) {
      return getStateStore().getProjectName();
    }
    return myName;
  }

  protected abstract IProjectStore getStateStore();

  public void init(@Nullable ProgressIndicator indicator) {
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
    return creationTrace;
  }
}
