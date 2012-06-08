/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.cvsSupport2.ui;

import com.intellij.CvsBundle;
import com.intellij.cvsSupport2.config.ui.ConfigureCvsGlobalSettingsDialog;
import com.intellij.cvsSupport2.config.ui.CvsConfigurationsListEditor;
import com.intellij.cvsSupport2.errorHandling.CvsException;
import com.intellij.icons.AllIcons;
import com.intellij.lifecycle.PeriodicalTasksCloser;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowAnchor;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.ui.content.*;
import com.intellij.ui.errorView.ContentManagerProvider;
import com.intellij.ui.errorView.ErrorViewFactory;
import com.intellij.util.ui.ErrorTreeView;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * author: lesya
 */
public class CvsTabbedWindow {
  private final Project myProject;
  private Editor myOutput = null;
  private ErrorTreeView myErrorsView;
  private boolean myIsInitialized;
  private boolean myIsDisposed;

  private static final Logger LOG = Logger.getInstance("#com.intellij.cvsSupport2.ui.CvsTabbedWindow");
  private ContentManager myContentManager;

  public CvsTabbedWindow(Project project) {
    myProject = project;
    Disposer.register(project, new Disposable() {
      public void dispose() {
        if (myOutput != null) {
          EditorFactory.getInstance().releaseEditor(myOutput);
          myOutput = null;
        }
        if (myErrorsView != null) {
          myErrorsView.dispose();
          myErrorsView = null;
        }

        LOG.assertTrue(!myIsDisposed);
        try {
          if (!myIsInitialized) return;
          ToolWindowManager toolWindowManager = ToolWindowManager.getInstance(myProject);
          toolWindowManager.unregisterToolWindow(ToolWindowId.CVS);
        }
        finally {
          myIsDisposed = true;
        }
      }
    });
  }

  private void initialize() {
    if (myIsInitialized) {
      return;
    }

    myIsInitialized = true;
    myIsDisposed = false;

    myContentManager = ContentFactory.SERVICE.getInstance().createContentManager(true, myProject);
    myContentManager.addContentManagerListener(new ContentManagerAdapter() {
      public void contentRemoved(ContentManagerEvent event) {
        JComponent component = event.getContent().getComponent();
        JComponent removedComponent = component instanceof CvsTabbedWindowComponent ?
                                      ((CvsTabbedWindowComponent)component).getComponent() : component;
        if (removedComponent == myErrorsView) {
          myErrorsView.dispose();
          myErrorsView = null;
        }
        else if (myOutput != null && removedComponent == myOutput.getComponent()) {
          EditorFactory.getInstance().releaseEditor(myOutput);
          myOutput = null;
        }
      }
    });

    ToolWindowManager toolWindowManager = ToolWindowManager.getInstance(myProject);
    ToolWindow toolWindow =
      toolWindowManager.registerToolWindow(ToolWindowId.CVS, myContentManager.getComponent(), ToolWindowAnchor.BOTTOM);
    toolWindow.setIcon(AllIcons.Providers.Cvs);
    toolWindow.installWatcher(myContentManager);
  }

  public static CvsTabbedWindow getInstance(Project project) {
    return PeriodicalTasksCloser.getInstance().safeGetService(project, CvsTabbedWindow.class);
  }

  private int getComponentAt(int i, boolean select) {
    if (select) getContentManager().setSelectedContent(getContentManager().getContent(i));
    return i;
  }

  public interface DeactivateListener {
    void deactivated();
  }

  public int addTab(String s,
                    JComponent component,
                    boolean selectTab,
                    boolean replaceContent,
                    boolean lockable,
                    boolean addDefaultToolbar,
                    @Nullable final ActionGroup toolbarActions,
                    @NonNls String helpId) {

    int existing = getComponentNumNamed(s);
    if (existing != -1) {
      Content existingContent = getContentManager().getContent(existing);
      final JComponent existingComponent = existingContent.getComponent();
      if (existingComponent instanceof DeactivateListener) {
        ((DeactivateListener) existingComponent).deactivated();
      }
      if (! replaceContent) {
        getContentManager().setSelectedContent(existingContent);
        return existing;
      }
      else if (!existingContent.isPinned()) {
        getContentManager().removeContent(existingContent, true);
        existingContent.release();
      }
    }

    CvsTabbedWindowComponent newComponent = new CvsTabbedWindowComponent(component,
                                                                         addDefaultToolbar, toolbarActions, getContentManager(), helpId);
    Content content = ContentFactory.SERVICE.getInstance().createContent(newComponent.getShownComponent(), s, lockable);
    newComponent.setContent(content);
    getContentManager().addContent(content);

    return getComponentAt(getContentManager().getContentCount() - 1, selectTab);
  }

  private int getComponentNumNamed(String s) {
    for (int i = 0; i < getContentManager().getContentCount(); i++) {
      if (s.equals(getContentManager().getContent(i).getDisplayName())) {
        return i;
      }
    }
    return -1;
  }

  public Editor addOutput(Editor output) {
    LOG.assertTrue(myOutput == null);
    if (myOutput == null) {
      addTab(CvsBundle.message("tab.title.cvs.output"), output.getComponent(), false, false, false, true, null, "cvs.cvsOutput");
      myOutput = output;
    }
    return myOutput;
  }

  public ErrorTreeView getErrorsTreeView() {
    if (myErrorsView == null) {
      myErrorsView = ErrorViewFactory.SERVICE.getInstance()
        .createErrorTreeView(myProject, null, true, new AnAction[]{(DefaultActionGroup)ActionManager.getInstance().getAction("CvsActions")},
                             new AnAction[]{new GlobalCvsSettingsAction(), new ReconfigureCvsRootAction()}, new ContentManagerProvider() {
          public ContentManager getParentContent() {
            return getContentManager();
          }
        });
      addTab(CvsBundle.message("tab.title.errors"), myErrorsView.getComponent(), true, false, true, false, null, "cvs.errors");
    }
    return myErrorsView;
  }

  public void hideErrors() {
    //if (myErrorsView == null) return;
    //Content content = getContent(myErrorsView);
    //removeContent(content);
    //myErrorsView = null;
  }

  public void ensureVisible(Project project) {
    if (project == null) return;
    ToolWindowManager toolWindowManager = ToolWindowManager.getInstance(project);
    if (toolWindowManager != null) {
      ToolWindow toolWindow = toolWindowManager.getToolWindow(ToolWindowId.CVS);
      if (toolWindow != null) {
        toolWindow.activate(null, false);
      }
    }
  }

  public ContentManager getContentManager() {
    initialize();
    return myContentManager;
  }

  public Editor getOutput() {
    return myOutput;
  }

  private static class GlobalCvsSettingsAction extends AnAction {
    public GlobalCvsSettingsAction() {
      super(CvsBundle.message("configure.global.cvs.settings.action.name"), null, AllIcons.Nodes.Cvs_global);
    }

    public void actionPerformed(AnActionEvent e) {
      new ConfigureCvsGlobalSettingsDialog(e.getProject()).show();
    }
  }

  private class ReconfigureCvsRootAction extends AnAction {
    public ReconfigureCvsRootAction() {
      super(CvsBundle.message("action.name.reconfigure.cvs.root"), null, AllIcons.Nodes.Cvs_roots);
    }

    public void update(AnActionEvent e) {
      super.update(e);
      Object data = ErrorTreeView.CURRENT_EXCEPTION_DATA_KEY.getData(e.getDataContext());
      e.getPresentation().setEnabled(data instanceof CvsException);

    }

    public void actionPerformed(AnActionEvent e) {
      Object data = ErrorTreeView.CURRENT_EXCEPTION_DATA_KEY.getData(e.getDataContext());
      CvsConfigurationsListEditor.reconfigureCvsRoot(((CvsException)data).getCvsRoot(), myProject);
    }
  }
}
