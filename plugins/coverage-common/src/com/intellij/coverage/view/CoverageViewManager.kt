// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.coverage.view;

import com.intellij.coverage.CoverageBundle;
import com.intellij.coverage.CoverageOptionsProvider;
import com.intellij.coverage.CoverageSuitesBundle;
import com.intellij.execution.configurations.RunConfigurationBase;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowAnchor;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.ui.AppUIUtil;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentManager;
import com.intellij.util.containers.DisposableWrapperList;
import kotlin.Unit;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@State(name = "CoverageViewManager", storages = @Storage(StoragePathMacros.PRODUCT_WORKSPACE_FILE))
@Service(Service.Level.PROJECT)
public final class CoverageViewManager implements PersistentStateComponent<CoverageViewManager.StateBean>, Disposable.Default {
  private static final Logger LOG = Logger.getInstance(CoverageViewManager.class);
  public static final @NonNls String TOOLWINDOW_ID = "Coverage";
  private final Project myProject;
  private ContentManager myContentManager;
  private StateBean myStateBean = new StateBean();
  private final Map<CoverageSuitesBundle, CoverageView> myViews = new HashMap<>();

  public CoverageViewManager(@NotNull Project project) {
    myProject = project;

    AppUIUtil.invokeLaterIfProjectAlive(myProject, () -> {
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

  public static CoverageViewManager getInstance(@NotNull Project project) {
    return project.getService(CoverageViewManager.class);
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

  public synchronized CoverageView getView(CoverageSuitesBundle suitesBundle) {
    return myViews.get(suitesBundle);
  }

  public CoverageSuitesBundle getOpenedSuite() {
    ContentManager manager = myContentManager;
    if (manager == null) return null;
    Content selectedContent = manager.getSelectedContent();
    if (selectedContent == null) return null;
    for (var entry : myViews.entrySet()) {
      Content content = manager.getContent(entry.getValue());
      if (content == selectedContent) {
        return entry.getKey();
      }
    }
    return null;
  }

  public void activateToolwindow(@NotNull CoverageView view) {
    myContentManager.setSelectedContent(myContentManager.getContent(view));
    ToolWindow toolWindow = ToolWindowManager.getInstance(myProject).getToolWindow(TOOLWINDOW_ID);
    LOG.assertTrue(toolWindow != null);
    toolWindow.activate(null, false);
  }

  public synchronized void createView(CoverageSuitesBundle suitesBundle, boolean activate) {
    CoverageView coverageView = myViews.get(suitesBundle);
    Content content;
    if (coverageView == null) {
      coverageView = new CoverageView(myProject, suitesBundle, myStateBean);
      myViews.put(suitesBundle, coverageView);
      content = myContentManager.getFactory().createContent(coverageView, getDisplayName(suitesBundle), false);
      myContentManager.addContent(content);
    }
    else {
      content = myContentManager.getContent(coverageView);
    }
    myContentManager.setSelectedContent(content);

    if (CoverageOptionsProvider.getInstance(myProject).activateViewOnRun() && activate) {
      activateToolwindow(coverageView);
    }
  }

  public void closeView(CoverageSuitesBundle suitesBundle) {
    CoverageView oldView = myViews.remove(suitesBundle);
    if (oldView != null) {
      oldView.saveSize();
      ApplicationManager.getApplication().invokeLater(() -> {
        Content content = myContentManager.getContent(oldView);
        if (content != null) {
          myContentManager.removeContent(content, false);
        }
      });
    }
  }

  @NlsSafe
  public static String getDisplayName(@NotNull CoverageSuitesBundle suitesBundle) {
    RunConfigurationBase<?> configuration = suitesBundle.getRunConfiguration();
    return configuration != null ? configuration.getName() : suitesBundle.getPresentableName();
  }

  /**
   * @deprecated Use {@link #getView(CoverageSuitesBundle)} instead
   */
  @Deprecated
  public CoverageView getToolwindow(CoverageSuitesBundle suitesBundle) {
    return getView(suitesBundle);
  }

  /**
   * @deprecated Use {@link #activateToolwindow(CoverageView)} instead
   */
  @Deprecated
  public void activateToolwindow(@NotNull CoverageView view, boolean activate) {
    if (activate) {
      activateToolwindow(view);
    }
  }

  /**
   * @deprecated Use {@link #createView(CoverageSuitesBundle, boolean)} instead
   */
  @Deprecated
  public void createToolWindow(CoverageSuitesBundle suitesBundle, boolean activate) {
    createView(suitesBundle, activate);
  }

  public static final class StateBean {
    private boolean myFlattenPackages = false;
    public boolean myAutoScrollToSource = false;
    public boolean myAutoScrollFromSource = false;
    public List<Integer> myColumnSize;
    public boolean myAscendingOrder = true;
    public int mySortingColumn = 0;
    private boolean myHideFullyCovered = false;
    private boolean myShowOnlyModified = true;
    private boolean myDefaultFilters = true;

    private final DisposableWrapperList<CoverageViewSettingsListener> myListeners = new DisposableWrapperList<>();

    public boolean isFlattenPackages() {
      return myFlattenPackages;
    }

    public void setFlattenPackages(boolean flattenPackages) {
      if (myFlattenPackages != flattenPackages) {
        myFlattenPackages = flattenPackages;
        fireChanged();
      }
    }

    public boolean isHideFullyCovered() {
      return myHideFullyCovered;
    }

    public void setHideFullyCovered(boolean hideFullyCovered) {
      if (myHideFullyCovered != hideFullyCovered) {
        myHideFullyCovered = hideFullyCovered;
        myDefaultFilters = false;
        fireChanged();
      }
    }

    public boolean isShowOnlyModified() {
      return myShowOnlyModified;
    }

    public void setShowOnlyModified(boolean showOnlyModified) {
      if (myShowOnlyModified != showOnlyModified) {
        myShowOnlyModified = showOnlyModified;
        myDefaultFilters = false;
        fireChanged();
      }
    }

    public boolean isDefaultFilters() {
      return myDefaultFilters;
    }

    public void addListener(Disposable disposable, CoverageViewSettingsListener listener) {
      myListeners.add(listener, disposable);
    }


    private void fireChanged() {
      for (CoverageViewSettingsListener listener : myListeners) {
        listener.onSettingsChanged(this);
      }
    }
  }

  public interface CoverageViewSettingsListener {
    void onSettingsChanged(StateBean stateBean);
  }
}
