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
package com.intellij.cvsSupport2.ui;

import com.intellij.CvsBundle;
import com.intellij.cvsSupport2.config.ui.ConfigureCvsGlobalSettingsDialog;
import com.intellij.cvsSupport2.config.ui.CvsConfigurationsListEditor;
import com.intellij.cvsSupport2.errorHandling.CvsException;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.EditorSettings;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentManager;
import com.intellij.ui.content.ContentManagerAdapter;
import com.intellij.ui.content.ContentManagerEvent;
import com.intellij.ui.errorView.ContentManagerProvider;
import com.intellij.ui.errorView.ErrorViewFactory;
import com.intellij.util.ui.ErrorTreeView;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * author: lesya
 */
public class CvsTabbedWindow implements Disposable {

  private final Project myProject;
  private Editor myOutput = null;
  private ErrorTreeView myErrorsView;

  public CvsTabbedWindow(Project project) {
    myProject = project;
    ApplicationManager.getApplication().invokeLater(() -> {
      if (myProject.isDisposed()) return;
      final ToolWindow toolWindow = getToolWindow();
      final ContentManager contentManager = toolWindow.getContentManager();
      contentManager.addContentManagerListener(new ContentManagerAdapter() {
        public void contentRemoved(ContentManagerEvent event) {
          final JComponent component = event.getContent().getComponent();
          final JComponent removedComponent = component instanceof CvsTabbedWindowComponent ?
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
      toolWindow.installWatcher(contentManager);
    });
  }

  public void dispose() {
    if (myOutput != null) {
      EditorFactory.getInstance().releaseEditor(myOutput);
      myOutput = null;
    }
    if (myErrorsView != null) {
      myErrorsView.dispose();
      myErrorsView = null;
    }
  }

  @NotNull
  private ToolWindow getToolWindow() {
    final ToolWindow toolWindow = ToolWindowManager.getInstance(myProject).getToolWindow(ToolWindowId.VCS);
    assert toolWindow != null : "Version Control ToolWindow should be available at this point.";
    return toolWindow;
  }

  public static CvsTabbedWindow getInstance(Project project) {
    return ServiceManager.getService(project, CvsTabbedWindow.class);
  }

  public interface DeactivateListener {
    void deactivated();
  }

  public void addTab(String title,
                     JComponent component,
                     boolean selectTab,
                     boolean replaceContent,
                     boolean lockable,
                     boolean addDefaultToolbar,
                     @Nullable final ActionGroup toolbarActions,
                     @NonNls String helpId) {
    final ContentManager contentManager = getToolWindow().getContentManager();
    final Content existingContent = contentManager.findContent(title);
    if (existingContent != null) {
      final JComponent existingComponent = existingContent.getComponent();
      if (existingComponent instanceof DeactivateListener) {
        ((DeactivateListener) existingComponent).deactivated();
      }
      if (!replaceContent) {
        contentManager.setSelectedContent(existingContent);
        return;
      }
      else if (!existingContent.isPinned()) {
        contentManager.removeContent(existingContent, true);
        existingContent.release();
      }
    }
    final CvsTabbedWindowComponent newComponent =
      new CvsTabbedWindowComponent(component, addDefaultToolbar, toolbarActions, contentManager, helpId);
    final Content content = contentManager.getFactory().createContent(newComponent.getShownComponent(), title, lockable);
    newComponent.setContent(content);
    contentManager.addContent(content);
    if (selectTab) {
      getToolWindow().activate(null, false);
    }
  }

  public ErrorTreeView getErrorsTreeView() {
    if (myErrorsView == null) {
      myErrorsView = ErrorViewFactory.SERVICE.getInstance()
        .createErrorTreeView(myProject, null, true, new AnAction[]{(DefaultActionGroup)ActionManager.getInstance().getAction("CvsActions")},
                             new AnAction[]{new GlobalCvsSettingsAction(), new ReconfigureCvsRootAction()}, new ContentManagerProvider() {
          public ContentManager getParentContent() {
            return getToolWindow().getContentManager();
          }
        });
      addTab(CvsBundle.message("tab.title.errors"), myErrorsView.getComponent(), true, false, true, false, null, "cvs.errors");
    }
    getToolWindow().activate(null, false);
    return myErrorsView;
  }

  public Editor getOutput() {
    if (myOutput == null) {
      final Editor outputEditor = createOutputEditor(myProject);
      addTab(CvsBundle.message("tab.title.cvs.output"), outputEditor.getComponent(), false, false, false, true, null, "cvs.cvsOutput");
      myOutput = outputEditor;
    }
    return myOutput;
  }

  @NotNull
  private static Editor createOutputEditor(Project project) {
    final Editor result = EditorFactory.getInstance().createViewer(EditorFactory.getInstance().createDocument(""), project);
    final EditorSettings editorSettings = result.getSettings();
    editorSettings.setLineMarkerAreaShown(false);
    editorSettings.setLineNumbersShown(false);
    editorSettings.setIndentGuidesShown(false);
    editorSettings.setFoldingOutlineShown(false);
    return result;
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
