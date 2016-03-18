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
package com.intellij.vcs.log.impl;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.ClearableLazyValue;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.VcsRoot;
import com.intellij.vcs.log.data.VcsLogDataManager;
import com.intellij.vcs.log.data.VcsLogTabsProperties;
import com.intellij.vcs.log.ui.VcsLogPanel;
import com.intellij.vcs.log.ui.VcsLogUiImpl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Arrays;
import java.util.Collection;

public class VcsLogProjectManager {
  @NotNull private final Project myProject;
  @NotNull private final VcsLogTabsProperties myUiProperties;

  @NotNull
  private final LazyVcsLogManager myLogManager = new LazyVcsLogManager();
  private volatile VcsLogUiImpl myUi;
  @Nullable private Runnable myRecreateMainLogHandler;

  public VcsLogProjectManager(@NotNull Project project, @NotNull VcsLogTabsProperties uiProperties) {
    myProject = project;
    myUiProperties = uiProperties;
  }

  @Nullable
  public VcsLogDataManager getDataManager() {
    VcsLogManager cached = myLogManager.getCached();
    if (cached == null) return null;
    return cached.getDataManager();
  }

  @NotNull
  private Collection<VcsRoot> getVcsRoots() {
    return Arrays.asList(ProjectLevelVcsManager.getInstance(myProject).getAllVcsRoots());
  }

  @NotNull
  public JComponent initMainLog(@NotNull String contentTabName) {
    myUi = myLogManager.getValue().createLogUi(VcsLogTabsProperties.MAIN_LOG_ID, contentTabName);
    return new VcsLogPanel(myLogManager.getValue(), myUi);
  }

  public void setRecreateMainLogHandler(@Nullable Runnable recreateMainLogHandler) {
    myRecreateMainLogHandler = recreateMainLogHandler;
  }

  /**
   * The instance of the {@link VcsLogUiImpl} or null if the log was not initialized yet.
   */
  @Nullable
  public VcsLogUiImpl getMainLogUi() {
    return myUi;
  }


  @Nullable
  public VcsLogManager getLogManager() {
    return myLogManager.getCached();
  }

  public void disposeLog() {
    myUi = null;
    myLogManager.drop();
  }

  public static VcsLogProjectManager getInstance(@NotNull Project project) {
    return ServiceManager.getService(project, VcsLogProjectManager.class);
  }

  private class LazyVcsLogManager extends ClearableLazyValue<VcsLogManager> {
    @NotNull
    @Override
    protected VcsLogManager compute() {
      return new VcsLogManager(myProject, myUiProperties, getVcsRoots(), myRecreateMainLogHandler);
    }

    @Override
    public void drop() {
      if (myValue != null) Disposer.dispose(myValue);
      super.drop();
    }

    @Nullable
    public VcsLogManager getCached() {
      return myValue;
    }
  }
}
