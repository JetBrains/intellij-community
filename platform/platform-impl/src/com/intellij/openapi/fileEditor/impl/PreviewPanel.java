/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.openapi.fileEditor.impl;

import com.intellij.icons.AllIcons;
import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.FileStatusManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindowAnchor;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.openapi.wm.impl.ToolWindowImpl;
import com.intellij.ui.JBColor;
import com.intellij.ui.docking.DockManager;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;

class PreviewPanel extends JPanel {
  private static final int HISTORY_LIMIT = 10;

  private final Project myProject;
  private final FileEditorManagerImpl myManager;
  private final DockManager myDockManager;
  private EditorWindow myWindow;
  private boolean myInitialized = false;
  private EditorsSplitters myEditorsSplitters;
  private ArrayList<VirtualFile> myHistory = new ArrayList<VirtualFile>();

  public PreviewPanel(Project project, FileEditorManagerImpl manager, DockManager dockManager) {
    myProject = project;
    myManager = manager;
    myDockManager = dockManager;
    setOpaque(true);
    setBackground(JBColor.DARK_GRAY);
  }

  private void initToolWindowIfNeed() {
    if (myInitialized) return;

    final ToolWindowImpl window = (ToolWindowImpl)ToolWindowManager.getInstance(myProject)
      .registerToolWindow(ToolWindowId.PREVIEW, this, ToolWindowAnchor.RIGHT, myProject, false);
    window.setIcon(AllIcons.Actions.PreviewDetails);

    myEditorsSplitters = new EditorsSplitters(myManager, myDockManager, false) {
      @Override
      protected void afterFileClosed(VirtualFile file) {
        window.setTitle(": (empty)");
      }

      @Override
      protected void afterFileOpen(VirtualFile file) {
        window.setTitle(": " +
                        StringUtil.getShortened(EditorTabbedContainer.calcTabTitle(myProject, file),
                                                UISettings.getInstance().EDITOR_TAB_TITLE_LIMIT));
      }

      @Override
      public void setTabsPlacement(int tabPlacement) {
        super.setTabsPlacement(UISettings.TABS_NONE);
      }

      @Override
      protected boolean showEmptyText() {
        return false;
      }
    };
    myEditorsSplitters.createCurrentWindow();

    myWindow = myEditorsSplitters.getCurrentWindow();

    setLayout(new GridLayout(1, 1));
    add(myEditorsSplitters);

    window.setTitleActions(new MoveToEditorTabsAction(), new CloseFileAction());

    myInitialized = true;
  }

  EditorWindow getWindow() {
    return myWindow;
  }

  EditorsSplitters getSplitters() {
    initToolWindowIfNeed();
    return myEditorsSplitters;
  }

  @Nullable
  VirtualFile getCurrentFile() {
    VirtualFile[] files = myWindow.getFiles();
    return files.length == 1 ? files[0] : null;
  }

  private class MoveToEditorTabsAction extends AnAction {
    public MoveToEditorTabsAction() {
      super(null, "Move to main tabs", AllIcons.Duplicates.SendToTheLeftGrayed);
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      VirtualFile virtualFile = getCurrentFile();
      if (virtualFile == null) {
        return;
      }

      myManager.openFileWithProviders(virtualFile, false, myManager.getCurrentWindow());
      closeCurrentFile();
    }

    @Override
    public void update(AnActionEvent e) {
      super.update(e);
      VirtualFile currentFile = getCurrentFile();
      e.getPresentation().setEnabled(currentFile != null);
      if (currentFile == null) return;

      if (isModified(currentFile)) {
        e.getPresentation().setIcon(AllIcons.Duplicates.SendToTheLeft);
      }
      else {
        e.getPresentation().setIcon(AllIcons.Duplicates.SendToTheLeftGrayed);
      }
    }
  }

  private boolean isModified(VirtualFile currentFile) {
    return FileStatus.MODIFIED == FileStatusManager.getInstance(myProject).getStatus(currentFile);
  }

  //returns last open file if it has "modified" status
  @Nullable
  VirtualFile closeCurrentFile() {
    VirtualFile virtualFile = getCurrentFile();
    if (virtualFile == null) return null;
    if (!myHistory.contains(virtualFile)) {
      myHistory.add(virtualFile);
      while (myHistory.size() > HISTORY_LIMIT) {
        myHistory.remove(0);
      }
    }
    myWindow.closeFile(virtualFile);
    this.revalidate();
    this.repaint();
    return isModified(virtualFile) ? virtualFile : null;
  }

  private class CloseFileAction extends AnAction {
    public CloseFileAction() {
      super(null, "Close", AllIcons.Actions.Close);
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      if (getCurrentFile() == null) return;
      closeCurrentFile();
      ToolWindowManager.getInstance(myProject).getToolWindow(ToolWindowId.PREVIEW).hide(null);
    }

    @Override
    public void update(AnActionEvent e) {
      super.update(e);
      e.getPresentation().setEnabled(getCurrentFile() != null);
    }
  }
}
