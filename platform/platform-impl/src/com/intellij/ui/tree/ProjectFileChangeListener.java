// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.tree;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.BulkFileListener;
import com.intellij.openapi.vfs.newvfs.events.VFileCopyEvent;
import com.intellij.openapi.vfs.newvfs.events.VFileCreateEvent;
import com.intellij.openapi.vfs.newvfs.events.VFileDeleteEvent;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import com.intellij.openapi.vfs.newvfs.events.VFileMoveEvent;
import com.intellij.util.concurrency.Invoker;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.function.BiConsumer;

import static com.intellij.openapi.vfs.VfsUtilCore.isAncestor;

public final class ProjectFileChangeListener implements BulkFileListener {
  private final BiConsumer<? super Module, VirtualFile> consumer;
  private final Project project;
  private final Invoker invoker;

  public ProjectFileChangeListener(@NotNull Invoker invoker, @NotNull Project project, @NotNull BiConsumer<Module, VirtualFile> consumer) {
    this.consumer = consumer;
    this.project = project;
    this.invoker = invoker;
  }

  @Override
  public void after(@NotNull List<? extends VFileEvent> events) {
    invoker.invokeLaterIfNeeded(() -> {
      for (VFileEvent event : events) {
        if (event instanceof VFileCreateEvent) {
          VFileCreateEvent create = (VFileCreateEvent)event;
          invalidate(create.getParent());
        }
        else if (event instanceof VFileCopyEvent) {
          VFileCopyEvent copy = (VFileCopyEvent)event;
          invalidate(copy.getNewParent());
        }
        else if (event instanceof VFileMoveEvent) {
          VFileMoveEvent move = (VFileMoveEvent)event;
          VirtualFile parent = move.getOldParent();
          if (parent != null) invalidate(parent);
          invalidate(move.getNewParent());
          invalidate(move.getFile());
        }
        else {
          VirtualFile file = event.getFile();
          if (file != null) {
            if (event instanceof VFileDeleteEvent) {
              VirtualFile parent = file.getParent();
              if (parent != null) invalidate(parent);
            }
            invalidate(file);
          }
        }
      }
    });
  }

  public void invalidate(@NotNull VirtualFile file) {
    invoker.invokeLaterIfNeeded(() -> {
      if (!project.isDisposed()) {
        Module module = ProjectFileIndex.getInstance(project).getModuleForFile(file);
        if (module != null) {
          consumer.accept(module, file);
        }
        else {
          VirtualFile ancestor = project.getBaseDir();
          if (ancestor != null && isAncestor(ancestor, file, false)) {
            // file does not belong to any content root, but it is located under the project directory
            consumer.accept(null, file);
          }
        }
      }
    });
  }
}
