// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileEditor.impl;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.IdeFrame;
import com.intellij.openapi.wm.ex.IdeFrameEx;
import com.intellij.openapi.wm.impl.IdeFrameImpl;
import com.intellij.openapi.wm.impl.ProjectFrameHelper;
import com.intellij.ui.docking.DockContainer;
import com.intellij.ui.docking.DockContainerFactory;
import com.intellij.ui.docking.DockManager;
import com.intellij.ui.docking.DockableContent;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class DockableEditorContainerFactory implements DockContainerFactory.Persistent {
  public static final @NonNls String TYPE = "file-editors";

  private final Project myProject;
  private final FileEditorManagerImpl myFileEditorManager;

  public DockableEditorContainerFactory(@NotNull Project project, @NotNull FileEditorManagerImpl fileEditorManager) {
    myProject = project;
    myFileEditorManager = fileEditorManager;
  }

  @Override
  public @NotNull DockContainer createContainer(@Nullable DockableContent content) {
    return createContainer(false);
  }

  private DockContainer createContainer(boolean loadingState) {
    Ref<DockableEditorTabbedContainer> containerRef = new Ref<>();
    EditorsSplitters splitters = new EditorsSplitters(myFileEditorManager) {
      @Override
      public void afterFileClosed$intellij_platform_ide_impl(@NotNull VirtualFile file) {
        containerRef.get().fireContentClosed(file);
      }

      @Override
      protected void afterFileOpen(@NotNull VirtualFile file) {
        containerRef.get().fireContentOpen(file);
      }

      @Override
      protected IdeFrameEx getFrame(@NotNull Project project) {
        IdeFrame frame = DockManager.getInstance(project).getIdeFrame(containerRef.get());
        return frame instanceof IdeFrameEx ? (IdeFrameEx)frame : ProjectFrameHelper.getFrameHelper(((IdeFrameImpl)frame));
      }

      @Override
      public boolean isFloating() {
        return true;
      }
    };
    if (!loadingState) {
      splitters.createCurrentWindow();
    }

    DockableEditorTabbedContainer container = new DockableEditorTabbedContainer(myProject, splitters, true);
    containerRef.set(container);
    container.getSplitters().startListeningFocus();
    return container;
  }

  @Override
  public DockContainer loadContainerFrom(Element element) {
    DockableEditorTabbedContainer container = (DockableEditorTabbedContainer)createContainer(true);
    container.getSplitters().readExternal(element.getChild("state"));
    return container;
  }
}
