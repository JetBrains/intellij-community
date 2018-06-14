// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.tree.project;

import com.intellij.openapi.extensions.AreaInstance;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootEvent;
import com.intellij.openapi.roots.ModuleRootListener;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.BulkFileListener;
import com.intellij.openapi.vfs.newvfs.events.*;
import com.intellij.psi.*;
import com.intellij.util.concurrency.Invoker;
import com.intellij.util.messages.MessageBusConnection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

import static com.intellij.ProjectTopics.PROJECT_ROOTS;
import static com.intellij.openapi.util.registry.Registry.is;
import static com.intellij.openapi.vfs.VfsUtilCore.isAncestor;
import static com.intellij.openapi.vfs.VirtualFileManager.VFS_CHANGES;
import static com.intellij.psi.util.PsiUtilCore.getVirtualFile;

public abstract class ProjectFileListener {
  private final Project project;
  private final Invoker invoker;

  public ProjectFileListener(@NotNull Project project, @NotNull Invoker invoker) {
    this.project = project;
    this.invoker = invoker;
    MessageBusConnection connection = project.getMessageBus().connect(invoker);
    connection.subscribe(PROJECT_ROOTS, new ModuleRootListener() {
      @Override
      public void rootsChanged(ModuleRootEvent event) {
        invoker.invokeLaterIfNeeded(ProjectFileListener.this::updateFromRoot);
      }
    });
    connection.subscribe(VFS_CHANGES, new BulkFileListener() {
      @Override
      public void after(@NotNull List<? extends VFileEvent> events) {
        invoker.invokeLaterIfNeeded(() -> {
          for (VFileEvent event: events) {
            if (event instanceof VFileCreateEvent) {
              VFileCreateEvent create = (VFileCreateEvent)event;
              updateFromFile(create.getParent());
            }
            else if (event instanceof VFileCopyEvent) {
              VFileCopyEvent copy = (VFileCopyEvent)event;
              updateFromFile(copy.getNewParent());
            }
            else if (event instanceof VFileMoveEvent) {
              VFileMoveEvent move = (VFileMoveEvent)event;
              updateFromFile(move.getNewParent());
              updateFromFile(move.getOldParent());
              updateFromFile(move.getFile());
            }
            else {
              VirtualFile file = event.getFile();
              if (file != null) {
                if (event instanceof VFileDeleteEvent) {
                  VirtualFile parent = file.getParent();
                  if (parent != null) updateFromFile(parent);
                }
                updateFromFile(file);
              }
            }
          }
        });
      }
    });
    PsiManager.getInstance(project).addPsiTreeChangeListener(new PsiTreeChangeAdapter() {
      @Override
      public void childAdded(@NotNull PsiTreeChangeEvent event) {
        if (event.getNewChild() instanceof PsiWhiteSpace) return; // optimization
        childrenChanged(event);
      }

      @Override
      public void childRemoved(@NotNull PsiTreeChangeEvent event) {
        if (event.getOldChild() instanceof PsiWhiteSpace) return; // optimization
        childrenChanged(event);
      }

      @Override
      public void childReplaced(@NotNull PsiTreeChangeEvent event) {
        if (event.getOldChild() instanceof PsiWhiteSpace && event.getNewChild() instanceof PsiWhiteSpace) return; // optimization
        childrenChanged(event);
      }

      @Override
      public void childrenChanged(@NotNull PsiTreeChangeEvent event) {
        updateFromElement(event.getParent());
      }

      @Override
      public void childMoved(@NotNull PsiTreeChangeEvent event) {
        updateFromElement(event.getOldParent());
        updateFromElement(event.getNewParent());
      }

      @Override
      public void propertyChanged(@NotNull PsiTreeChangeEvent event) {
      }
    }, invoker);
  }

  protected abstract void updateFromRoot();

  protected abstract void updateFromFile(@NotNull VirtualFile file, @NotNull AreaInstance area);

  public final void updateFromFile(@Nullable VirtualFile file) {
    if (file == null) return;
    invoker.invokeLaterIfNeeded(() -> {
      AreaInstance area = findArea(file, project);
      if (area != null) updateFromFile(file, area);
    });
  }

  public final void updateFromElement(@Nullable PsiElement element) {
    updateFromFile(getVirtualFile(element));
  }

  @Nullable
  public static AreaInstance findArea(@NotNull VirtualFile file, @Nullable Project project) {
    if (project == null || project.isDisposed()) return null;
    Module module = ProjectFileIndex.getInstance(project).getModuleForFile(file, false);
    if (module != null) return module.isDisposed() ? null : module;
    if (!is("projectView.show.base.dir")) return null;
    VirtualFile ancestor = project.getBaseDir();
    // file does not belong to any content root, but it is located under the project directory
    if (ancestor == null || !isAncestor(ancestor, file, false)) return null;
    PsiManager manager = PsiManager.getInstance(project);
    PsiElement element = file.isDirectory() ? manager.findDirectory(file) : manager.findFile(file);
    return element == null ? null : project; // ensure that the corresponding file can be shown
  }
}
