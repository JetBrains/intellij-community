// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.coverage.view;

import com.intellij.coverage.CoverageBundle;
import com.intellij.coverage.CoverageDataManager;
import com.intellij.coverage.CoverageOptionsProvider;
import com.intellij.coverage.CoverageSuitesBundle;
import com.intellij.execution.configurations.RunConfigurationBase;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.AppUIExecutor;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.components.StoragePathMacros;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowAnchor;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentManager;
import kotlin.Unit;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@State(name = "CoverageViewManager", storages = @Storage(StoragePathMacros.PRODUCT_WORKSPACE_FILE))
public final class CoverageViewManager implements PersistentStateComponent<CoverageViewManager.StateBean>, Disposable {
  private static final Logger LOG = Logger.getInstance(CoverageViewManager.class);
  public static final @NonNls String TOOLWINDOW_ID = "Coverage";
  private final Project myProject;
  private ContentManager myContentManager;
  private StateBean myStateBean = new StateBean();
  private final Map<String, CoverageView> myViews = new HashMap<>();
  private boolean myReady;

  public CoverageViewManager(@NotNull Project project) {
    myProject = project;

    AppUIExecutor.onUiThread().expireWith(this).submit(() -> {
      ToolWindow toolWindow = ToolWindowManager.getInstance(project).registerToolWindow(TOOLWINDOW_ID, builder -> {
        builder.sideTool = true;
        builder.icon = AllIcons.Toolwindows.ToolWindowCoverage;
        builder.anchor = ToolWindowAnchor.RIGHT;
        builder.stripeTitle = CoverageBundle.messagePointer("coverage.view.title");
        return Unit.INSTANCE;
      });
      toolWindow.setHelpId(CoverageView.HELP_ID);
      myContentManager = toolWindow.getContentManager();
    });
  }

  @Override
  public void dispose() {
  }

  @Override
  public StateBean getState() {
    if (!myViews.isEmpty()) {
      final CoverageView view = myViews.values().iterator().next();
      view.saveSize();
    }
    return myStateBean;
  }

  @Override
  public void loadState(@NotNull StateBean state) {
    myStateBean = state;
  }

  public StateBean getStateBean() {
    return myStateBean;
  }

  public CoverageView getToolwindow(CoverageSuitesBundle suitesBundle) {
    return myViews.get(getDisplayName(suitesBundle));
  }

  public void activateToolwindow(@NotNull CoverageView view, boolean requestFocus) {
    ToolWindow toolWindow = ToolWindowManager.getInstance(myProject).getToolWindow(TOOLWINDOW_ID);
    if (requestFocus) {
      myContentManager.setSelectedContent(myContentManager.getContent(view));
      LOG.assertTrue(toolWindow != null);
      toolWindow.activate(null, false);
    }
  }

  public static CoverageViewManager getInstance(@NotNull Project project) {
    return project.getService(CoverageViewManager.class);
  }

  public void createToolWindow(@NlsSafe String displayName, boolean defaultFileProvider) {
    final CoverageView coverageView = new CoverageView(myProject, CoverageDataManager.getInstance(myProject), myStateBean);
    myViews.put(displayName, coverageView);
    Content content = myContentManager.getFactory().createContent(coverageView, displayName, false);
    myContentManager.addContent(content);
    myContentManager.setSelectedContent(content);

    if (CoverageOptionsProvider.getInstance(myProject).activateViewOnRun() && defaultFileProvider) {
      activateToolwindow(coverageView, true);
    }
  }

  void closeView(String displayName) {
    CoverageView oldView = myViews.remove(displayName);
    if (oldView != null) {
      oldView.saveSize();
      Content content = myContentManager.getContent(oldView);
      ApplicationManager.getApplication().invokeLater(() -> {
        if (content != null) {
          myContentManager.removeContent(content, false);
        }
      });
    }
    setReady(false);
  }

  public boolean isReady() {
    return myReady;
  }

  public void setReady(boolean ready) {
    myReady = ready;
  }

  public static String getDisplayName(CoverageSuitesBundle suitesBundle) {
    RunConfigurationBase<?> configuration = suitesBundle.getRunConfiguration();
    return configuration != null ? configuration.getName() : suitesBundle.getPresentableName();
  }

  public static final class StateBean {
    public boolean myFlattenPackages = false;
    public boolean myAutoScrollToSource = false;
    public boolean myAutoScrollFromSource = false;
    public List<Integer> myColumnSize;
    public boolean myAscendingOrder = true;
    public int mySortingColumn = 0;
  }
}
