// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.fileEditor.impl;

import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorProvider;
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx;
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

public final class FilePreviewPanelProvider extends PreviewPanelProvider<VirtualFile, Pair<FileEditor[], FileEditorProvider[]>> {
  public static final PreviewProviderId<VirtualFile, Pair<FileEditor[], FileEditorProvider[]>> ID = PreviewProviderId.create("Files");

  private final FileEditorManagerImpl myManager;
  private final Project myProject;

  private final EditorWindow myWindow;
  private final EditorsSplitters myEditorsSplitters;

  public FilePreviewPanelProvider(@NotNull Project project) {
    super(ID);
    myProject = project;
    myManager = (FileEditorManagerImpl)FileEditorManagerEx.getInstanceEx(project);
    myEditorsSplitters = new MyEditorsSplitters(myManager, DockManager.getInstance(project), false);
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

  private final class MyEditorsSplitters extends EditorsSplitters {
    private MyEditorsSplitters(final FileEditorManagerImpl manager, DockManager dockManager, boolean createOwnDockableContainer) {
      super(manager, dockManager, createOwnDockableContainer);
    }

    @Override
    protected void afterFileClosed(@NotNull VirtualFile file) {
      PreviewManager.SERVICE.close(myProject, getId(), file);
    }

    @NotNull
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
