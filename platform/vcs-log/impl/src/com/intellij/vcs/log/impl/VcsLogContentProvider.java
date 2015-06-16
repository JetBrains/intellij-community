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
package com.intellij.vcs.log.impl;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.VcsListener;
import com.intellij.openapi.vcs.changes.ui.ChangesViewContentEP;
import com.intellij.openapi.vcs.changes.ui.ChangesViewContentProvider;
import com.intellij.ui.components.JBPanel;
import com.intellij.util.Function;
import com.intellij.util.NotNullFunction;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.vcs.log.VcsLogSettings;
import com.intellij.vcs.log.data.VcsLogUiProperties;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.Arrays;

/**
 * Provides the Content tab to the ChangesView log toolwindow.
 * <p/>
 * Delegates to the VcsLogManager.
 */
public class VcsLogContentProvider implements ChangesViewContentProvider {

  public static final String TAB_NAME = "Log";
  private static final Logger LOG = Logger.getInstance(VcsLogContentProvider.class);

  @NotNull private final Project myProject;
  @NotNull private final VcsLogManager myLogManager;
  @NotNull private final ProjectLevelVcsManager myVcsManager;
  @NotNull private final JPanel myContainer = new JBPanel(new BorderLayout());
  private MessageBusConnection myConnection;

  public VcsLogContentProvider(@NotNull Project project,
                               @NotNull ProjectLevelVcsManager manager,
                               @NotNull VcsLogSettings settings,
                               @NotNull VcsLogUiProperties uiProperties) {
    myProject = project;
    myVcsManager = manager;
    myLogManager = new VcsLogManager(project, settings, uiProperties);
  }

  @Nullable
  public static VcsLogManager findLogManager(@NotNull Project project) {
    final ChangesViewContentEP[] eps = project.getExtensions(ChangesViewContentEP.EP_NAME);
    ChangesViewContentEP ep = ContainerUtil.find(eps, new Condition<ChangesViewContentEP>() {
      @Override
      public boolean value(ChangesViewContentEP ep) {
        return ep.getClassName().equals(VcsLogContentProvider.class.getName());
      }
    });
    if (ep == null) {
      LOG.warn("Proper content provider ep not found among [" + toString(eps) + "]");
      return null;
    }
    ChangesViewContentProvider instance = ep.getInstance(project);
    if (!(instance instanceof VcsLogContentProvider)) {
      LOG.error("Class name matches, but the class doesn't. class name: " + ep.getClassName() + ", class: " + ep.getClass());
      return null;
    }
    VcsLogContentProvider provider = (VcsLogContentProvider)instance;
    return provider.myLogManager;
  }

  @NotNull
  private static String toString(@NotNull ChangesViewContentEP[] eps) {
    return StringUtil.join(eps, new Function<ChangesViewContentEP, String>() {
      @Override
      public String fun(ChangesViewContentEP ep) {
        return String.format("%s-%s-%s", ep.tabName, ep.className, ep.predicateClassName);
      }
    }, ",");
  }

  @Override
  public JComponent initContent() {
    myConnection = myProject.getMessageBus().connect();
    myConnection.subscribe(ProjectLevelVcsManager.VCS_CONFIGURATION_CHANGED, new MyVcsListener());
    initContentInternal();
    return myContainer;
  }

  private void initContentInternal() {
    ApplicationManager.getApplication().assertIsDispatchThread();
    myContainer.add(myLogManager.initContent(Arrays.asList(myVcsManager.getAllVcsRoots()), TAB_NAME), BorderLayout.CENTER);
  }

  @Override
  public void disposeContent() {
    myConnection.disconnect();
    myContainer.removeAll();
    Disposer.dispose(myLogManager);
  }

  private class MyVcsListener implements VcsListener {
    @Override
    public void directoryMappingChanged() {
      ApplicationManager.getApplication().invokeLater(new Runnable() {
        @Override
        public void run() {
          myContainer.removeAll();
          Disposer.dispose(myLogManager);

          initContentInternal();
        }
      });
    }
  }

  public static class VcsLogVisibilityPredicate implements NotNullFunction<Project, Boolean> {
    @NotNull
    @Override
    public Boolean fun(Project project) {
      return !VcsLogManager.findLogProviders(Arrays.asList(ProjectLevelVcsManager.getInstance(project).getAllVcsRoots()), project)
        .isEmpty();
    }
  }
}
