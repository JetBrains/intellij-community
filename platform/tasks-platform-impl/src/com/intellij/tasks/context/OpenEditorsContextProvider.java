// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.tasks.context;

import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.impl.DockableEditorTabbedContainer;
import com.intellij.openapi.fileEditor.impl.FileEditorManagerImpl;
import com.intellij.openapi.project.Project;
import com.intellij.ui.docking.DockContainer;
import com.intellij.ui.docking.DockManager;
import com.intellij.ui.docking.impl.DockManagerImpl;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Dmitry Avdeev
 */
final class OpenEditorsContextProvider extends WorkingContextProvider {
  @Nullable
  private static FileEditorManagerImpl getFileEditorManager(@NotNull Project project) {
    FileEditorManager fileEditorManager = FileEditorManager.getInstance(project);
    return fileEditorManager instanceof FileEditorManagerImpl ? (FileEditorManagerImpl)fileEditorManager : null;
  }

  @NotNull
  @Override
  public String getId() {
    return "editors";
  }

  @NotNull
  @Override
  public String getDescription() {
    return "Open editors and positions";
  }

  @Override
  public void saveContext(@NotNull Project project, @NotNull Element element) {
    FileEditorManagerImpl fileEditorManager = getFileEditorManager(project);
    if (fileEditorManager != null) {
      fileEditorManager.getMainSplitters().writeExternal(element);
    }
    element.addContent(((DockManagerImpl)DockManager.getInstance(project)).getState());
  }

  @Override
  public void loadContext(@NotNull Project project, @NotNull Element element) {
    FileEditorManagerImpl fileEditorManager = getFileEditorManager(project);
    if (fileEditorManager != null) {
      fileEditorManager.loadState(element);
      fileEditorManager.getMainSplitters().openFiles();
    }

    Element dockState = element.getChild("state");
    if (dockState != null) {
      DockManagerImpl dockManager = (DockManagerImpl)DockManager.getInstance(project);
      dockManager.loadState(dockState);
      dockManager.readState();
    }
  }

  @Override
  public void clearContext(@NotNull Project project) {
    FileEditorManagerImpl fileEditorManager = getFileEditorManager(project);
    if (fileEditorManager != null) {
      fileEditorManager.closeAllFiles();
      fileEditorManager.getMainSplitters().clear();
    }

    DockManagerImpl dockManager = (DockManagerImpl)DockManager.getInstance(project);
    for (DockContainer container : dockManager.getContainers()) {
      if (container instanceof DockableEditorTabbedContainer) {
        container.closeAll();
      }
    }
  }
}
