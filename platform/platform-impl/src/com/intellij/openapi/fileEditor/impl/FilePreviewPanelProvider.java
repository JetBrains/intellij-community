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

import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorProvider;
import com.intellij.openapi.preview.PreviewManager;
import com.intellij.openapi.preview.PreviewPanelProvider;
import com.intellij.openapi.preview.PreviewProviderId;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.docking.DockManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

public class FilePreviewPanelProvider extends PreviewPanelProvider<VirtualFile, Pair<FileEditor[], FileEditorProvider[]>> {
  public static final PreviewProviderId<VirtualFile, Pair<FileEditor[], FileEditorProvider[]>> ID = PreviewProviderId.create("Files");

  private final FileEditorManagerImpl myManager;
  private final Project myProject;

  private EditorWindow myWindow;
  private EditorsSplitters myEditorsSplitters;

  public FilePreviewPanelProvider(@NotNull Project project, @NotNull FileEditorManagerImpl manager, @NotNull DockManager dockManager) {
    super(ID);
    myProject = project;
    myManager = manager;
    myEditorsSplitters = new MyEditorsSplitters(manager, dockManager, false);
    myEditorsSplitters.createCurrentWindow();
    myWindow = myEditorsSplitters.getCurrentWindow();
    myWindow.setTabsPlacement(UISettings.TABS_NONE);
  }

  @NotNull
  @Override
  protected JComponent getComponent() {
    return myEditorsSplitters;
  }

  @Override
  public boolean shouldBeEnabledByDefault() {
    return true;
  }

  @Override
  protected Pair<FileEditor[], FileEditorProvider[]> initComponent(VirtualFile file, boolean requestFocus) {
    return myManager.openFileWithProviders(file, requestFocus, myWindow);
  }

  @NotNull
  @Override
  protected String getTitle(@NotNull VirtualFile file) {
    return StringUtil.getShortened(EditorTabbedContainer.calcTabTitle(myProject, file), UISettings.getInstance().EDITOR_TAB_TITLE_LIMIT);
  }

  @Nullable
  @Override
  protected Icon getIcon(@NotNull VirtualFile file) {
    return file.getFileType().getIcon();
  }

  @Override
  public float getMenuOrder() {
    return 0;
  }

  @Override
  public boolean moveContentToStandardView(@NotNull VirtualFile file) {
    EditorWindow window = myManager.getCurrentWindow();
    if (window == null) { //main tab set is still not created, rare situation
      myManager.getMainSplitters().createCurrentWindow();
      window = myManager.getCurrentWindow();
    }
    myManager.openFileWithProviders(file, true, window);
    return true;
  }

  private class MyEditorsSplitters extends EditorsSplitters {
    public MyEditorsSplitters(final FileEditorManagerImpl manager, DockManager dockManager, boolean createOwnDockableContainer) {
      super(manager, dockManager, createOwnDockableContainer);
    }

    @Override
    protected void afterFileClosed(VirtualFile file) {
      PreviewManager previewManager = PreviewManager.SERVICE.getInstance(myProject);
      if (previewManager != null) {
        previewManager.close(getId(), file);
      }
    }

    @Override
    protected EditorWindow createEditorWindow() {
      return new EditorWindow(this) {
        @Override
        protected void onBeforeSetEditor(VirtualFile file) {
          List<VirtualFile> toMove = new ArrayList<VirtualFile>();
          for (EditorWithProviderComposite composite : getEditorsComposites()) {
            if (composite.isModified()) {
              toMove.add(composite.getFile());
            }
          }
          PreviewManager previewManager = PreviewManager.SERVICE.getInstance(myProject);
          if (previewManager != null) {
            for (VirtualFile virtualFile : toMove) {
              previewManager.moveToStandardPlace(getId(), virtualFile);
            }
          }
        }
      };
    }


    @Override
    public void setTabsPlacement(int tabPlacement) {
      super.setTabsPlacement(UISettings.TABS_NONE);
    }

    @Override
    public boolean isPreview() {
      return true;
    }
  }
}
