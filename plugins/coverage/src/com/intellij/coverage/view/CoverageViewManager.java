package com.intellij.coverage.view;

import com.intellij.coverage.CoverageDataManager;
import com.intellij.coverage.CoverageSuiteListener;
import com.intellij.ide.impl.ContentManagerWatcher;
import com.intellij.openapi.components.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowAnchor;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentManager;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

/**
 * User: anna
 * Date: 1/2/12
 */
@State(
    name = "CoverageViewManager",
    storages = {@Storage( file = "$WORKSPACE_FILE$")}
)
public class CoverageViewManager implements ProjectComponent, PersistentStateComponent<CoverageViewManager.StateBean> {
  public static final String TOOLWINDOW_ID = "Coverage View";
  private Project myProject;
  private final ToolWindowManager myToolWindowManager;
  private final CoverageDataManager myDataManager;
  private ContentManager myContentManager;
  private StateBean myStateBean = new StateBean();

  public CoverageViewManager(Project project, ToolWindowManager toolWindowManager, CoverageDataManager dataManager) {
    myProject = project;

    myToolWindowManager = toolWindowManager;
    myDataManager = dataManager;
  }

  public void projectOpened() {
    ToolWindow toolWindow = myToolWindowManager.registerToolWindow(TOOLWINDOW_ID, true, ToolWindowAnchor.RIGHT, myProject);
    toolWindow.setSplitMode(true, null);
    myContentManager = toolWindow.getContentManager();
    new ContentManagerWatcher(toolWindow, myContentManager);
    myDataManager.addSuiteListener(new CoverageSuiteListener() {
      public void beforeSuiteChosen() {
      }

      public void afterSuiteChosen() {
        final String presentableName = myDataManager.getCurrentSuitesBundle().getPresentableName();
        createToolWindow(presentableName);
      }
    }, myProject);
  }

  public void projectClosed() {
  }

  public void initComponent() {
  }

  public void disposeComponent() {
  }

  @NotNull
  public String getComponentName() {
    return "CoverageViewManager";
  }

  public StateBean getState() {
    return myStateBean;
  }

  public void loadState(StateBean state) {
    myStateBean = state;
  }

  public CoverageView getToolwindow(String presentableName) {
    return myViews.get(presentableName);
  }

  public void activateToolwindow(CoverageView view, boolean requestFocus) {
    ToolWindow toolWindow = ToolWindowManager.getInstance(myProject).getToolWindow(TOOLWINDOW_ID);
    if (requestFocus) {
      toolWindow.activate(null);
    }
  }

  public static CoverageViewManager getInstance(@NotNull Project project) {
    return ServiceManager.getService(project, CoverageViewManager.class);
  }

  private Map<String, CoverageView> myViews = new HashMap<String, CoverageView>();
  public void createToolWindow(String displayName) {
    final Content[] myContent = new Content[1];
    ToolWindow toolWindow = ToolWindowManager.getInstance(myProject).getToolWindow(TOOLWINDOW_ID);
    final CoverageView oldView = myViews.get(displayName);
    if (oldView != null) {
      final Content content = myContentManager.getContent(oldView);
      if (content != null) {
        myContentManager.removeContent(content, true);
      }
    }
    final CoverageView coverageView = new CoverageView(myProject, myDataManager, myStateBean);
    myViews.put(displayName, coverageView);
    myContent[0] = myContentManager.getFactory().createContent(coverageView, displayName, true);
    myContentManager.addContent(myContent[0]);
    myContentManager.setSelectedContent(myContent[0]);

    toolWindow.activate(null);
  }
  
  public static class StateBean {
    public boolean myFlattenPackages = false;
    public boolean myAutoScrollToSource = false;
    public boolean myAutoScrollFromSource = false;
  }
}
