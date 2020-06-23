// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.cvsSupport2.ui;

import com.intellij.CvsBundle;
import com.intellij.cvsSupport2.config.ui.ConfigureCvsGlobalSettingsDialog;
import com.intellij.cvsSupport2.config.ui.CvsConfigurationsListEditor;
import com.intellij.cvsSupport2.errorHandling.CvsException;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.EditorSettings;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.changes.ui.ChangesViewContentManager;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentManager;
import com.intellij.ui.content.ContentManagerEvent;
import com.intellij.ui.content.ContentManagerListener;
import com.intellij.ui.errorView.ContentManagerProvider;
import com.intellij.ui.errorView.ErrorViewFactory;
import com.intellij.util.ui.ErrorTreeView;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Objects;

public class CvsTabbedWindow implements Disposable {
  private final Project myProject;
  private Editor myOutput = null;
  private ErrorTreeView myErrorsView;

  public CvsTabbedWindow(Project project) {
    myProject = project;

    ApplicationManager.getApplication().invokeLater(() -> initToolWindow());
  }

  private void initToolWindow() {
    if (myProject.isDisposed()) {
      return;
    }

    final ToolWindow toolWindow = getToolWindow();
    final ContentManager contentManager = toolWindow.getContentManager();
    contentManager.addContentManagerListener(new ContentManagerListener() {
      @Override
      public void contentRemoved(@NotNull ContentManagerEvent event) {
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
  }

  @Override
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
    final ToolWindow toolWindow = ToolWindowManager.getInstance(myProject).getToolWindow(ChangesViewContentManager.TOOLWINDOW_ID);
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
        .createErrorTreeView(myProject, null, true, new AnAction[]{ActionManager.getInstance().getAction("CvsActions")},
                             new AnAction[]{new GlobalCvsSettingsAction(), new ReconfigureCvsRootAction()}, new ContentManagerProvider() {
          @Override
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
    GlobalCvsSettingsAction() {
      super(CvsBundle.messagePointer("configure.global.cvs.settings.action.name"), AllIcons.Nodes.Cvs_global);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      new ConfigureCvsGlobalSettingsDialog(e.getProject()).show();
    }
  }

  private class ReconfigureCvsRootAction extends AnAction {
    ReconfigureCvsRootAction() {
      super(CvsBundle.messagePointer("action.name.reconfigure.cvs.root"), AllIcons.Nodes.Cvs_roots);
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      Object data = e.getData(ErrorTreeView.CURRENT_EXCEPTION_DATA_KEY);
      e.getPresentation().setEnabled(data instanceof CvsException);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      Object data = e.getData(ErrorTreeView.CURRENT_EXCEPTION_DATA_KEY);
      CvsConfigurationsListEditor.reconfigureCvsRoot(((CvsException)Objects.requireNonNull(data)).getCvsRoot(), myProject);
    }
  }
}
