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
package com.intellij.openapi.fileEditor.impl;

import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorProvider;
import com.intellij.openapi.preview.PreviewManager;
import com.intellij.openapi.preview.PreviewPanelProvider;
import com.intellij.openapi.preview.PreviewProviderId;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.VfsPresentationUtil;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.ui.docking.DockManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class FilePreviewPanelProvider extends PreviewPanelProvider<VirtualFile, Pair<FileEditor[], FileEditorProvider[]>> {
  public static final PreviewProviderId<VirtualFile, Pair<FileEditor[], FileEditorProvider[]>> ID = PreviewProviderId.create("Files");

  private final FileEditorManagerImpl myManager;
  private final Project myProject;

  private final EditorWindow myWindow;
  private final EditorsSplitters myEditorsSplitters;

  public FilePreviewPanelProvider(@NotNull Project project, @NotNull FileEditorManagerImpl manager, @NotNull DockManager dockManager) {
    super(ID);
    myProject = project;
    myManager = manager;
    myEditorsSplitters = new MyEditorsSplitters(manager, dockManager, false);
    Disposer.register(this, myEditorsSplitters);
    myEditorsSplitters.createCurrentWindow();
    myWindow = myEditorsSplitters.getCurrentWindow();
    myWindow.setTabsPlacement(UISettings.TABS_NONE);
  }

  @Override
  public void dispose() {
  }

  @NotNull
  @Override
  protected JComponent getComponent() {
    return myEditorsSplitters;
  }

  @Override
  protected Pair<FileEditor[], FileEditorProvider[]> initComponent(VirtualFile file, boolean requestFocus) {
    Pair<FileEditor[], FileEditorProvider[]> result = myManager.openFileWithProviders(file, requestFocus, myWindow);
    IdeFocusManager.findInstance().doWhenFocusSettlesDown(() -> myWindow.requestFocus(true));
    return result;
  }

  @NotNull
  @Override
  protected String getTitle(@NotNull VirtualFile file) {
    return VfsPresentationUtil.getPresentableNameForUI(myProject, file);
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
  public void showInStandardPlace(@NotNull VirtualFile file) {
    EditorWindow window = myManager.getCurrentWindow();
    if (window == null) { //main tab set is still not created, rare situation
      myManager.getMainSplitters().createCurrentWindow();
      window = myManager.getCurrentWindow();
    }
    myManager.openFileWithProviders(file, true, window);
  }

  @Override
  public boolean isModified(VirtualFile content, boolean beforeReuse) {
    for (EditorWithProviderComposite composite : myEditorsSplitters.getEditorsComposites()) {
      if (composite.isModified() && Comparing.equal(composite.getFile(), content)) return true;
    }
    return false;
  }

  @Override
  public void release(@NotNull VirtualFile content) {
    myEditorsSplitters.closeFile(content, false);
  }

  @Override
  public boolean contentsAreEqual(@NotNull VirtualFile content1, @NotNull VirtualFile content2) {
    return Comparing.equal(content1, content2);
  }

  private class MyEditorsSplitters extends EditorsSplitters {
    private MyEditorsSplitters(final FileEditorManagerImpl manager, DockManager dockManager, boolean createOwnDockableContainer) {
      super(manager, dockManager, createOwnDockableContainer);
    }

    @Override
    protected void afterFileClosed(VirtualFile file) {
      PreviewManager.SERVICE.close(myProject, getId(), file);
    }

    @Override
    protected EditorWindow createEditorWindow() {
      return new EditorWindow(this) {
        @Override
        protected void onBeforeSetEditor(VirtualFile file) {
          for (EditorWithProviderComposite composite : getEditorsComposites()) {
            if (composite.isModified()) {
              //Estimation: no more than one file is modified at the same time
              PreviewManager.SERVICE.moveToStandardPlaceImpl(myProject, getId(), composite.getFile());
              return;
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
