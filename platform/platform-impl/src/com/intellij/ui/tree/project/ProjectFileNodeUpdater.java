// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.tree.project;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootEvent;
import com.intellij.openapi.roots.ModuleRootListener;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.BulkFileListener;
import com.intellij.openapi.vfs.newvfs.events.*;
import com.intellij.psi.*;
import com.intellij.util.concurrency.EdtExecutorService;
import com.intellij.util.concurrency.Invoker;
import com.intellij.util.containers.SmartHashSet;
import com.intellij.util.messages.MessageBusConnection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Set;

import static com.intellij.ProjectTopics.PROJECT_ROOTS;
import static com.intellij.openapi.vfs.VirtualFileManager.VFS_CHANGES;
import static com.intellij.psi.util.PsiUtilCore.getVirtualFile;

public abstract class ProjectFileNodeUpdater {
  private static final Logger LOG = Logger.getInstance(ProjectFileNodeUpdater.class);
  private final Ref<Set<VirtualFile>> reference = new Ref<>();
  private final Invoker invoker;
  private volatile boolean root;
  private volatile long time;
  private volatile int size;

  public ProjectFileNodeUpdater(@NotNull Project project, @NotNull Invoker invoker) {
    this.invoker = invoker;
    MessageBusConnection connection = project.getMessageBus().connect(invoker);
    connection.subscribe(PROJECT_ROOTS, new ModuleRootListener() {
      @Override
      public void rootsChanged(@NotNull ModuleRootEvent event) {
        updateFromRoot();
      }
    });
    connection.subscribe(VFS_CHANGES, new BulkFileListener() {
      @Override
      public void after(@NotNull List<? extends VFileEvent> events) {
        for (VFileEvent event : events) {
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

  /**
   * Notifies that project roots are changed.
   * The {@link #onInvokerThread} method will be executed with a small delay
   * after calling of this method.
   *
   * @see #getUpdatingDelay
   */
  public void updateFromRoot() {
    updateLater(null);
  }

  /**
   * Notifies that the specified file (or folder) is changed.
   * The {@link #onInvokerThread} method will be executed with a small delay
   * after last calling of this method,
   * i.e. a bunch of modified files will be reported together.
   *
   * @param file a modified virtual file
   * @see #getUpdatingDelay
   */
  public void updateFromFile(@Nullable VirtualFile file) {
    if (file != null) updateLater(file);
  }

  /**
   * Notifies that the specified PSI element is changed.
   * If this element corresponds to a virtual file,
   * it will be marked as changed.
   *
   * @param element a modified PSI element
   * @see #updateFromFile
   */
  public void updateFromElement(@Nullable PsiElement element) {
    updateFromFile(getVirtualFile(element));
  }

  /**
   * Notifies that all collected files should be reported as soon as possible.
   * Usually, it is needed to find an added file in a tree right after adding.
   */
  public void updateImmediately(@NotNull Runnable onDone) {
    invoker.runOrInvokeLater(() -> onInvokerThread(true)).onProcessed(o -> EdtExecutorService.getInstance().execute(onDone));
  }

  /**
   * @return a delay between an event and the {@link #onInvokerThread} method calling,
   * that is used to collect a bunch of changes
   */
  protected int getUpdatingDelay() {
    return 10;
  }

  private void updateLater(@Nullable VirtualFile file) {
    Set<VirtualFile> files;
    boolean start = false;
    synchronized (reference) {
      files = reference.get();
      if (files == null) {
        files = new SmartHashSet<>();
        time = System.currentTimeMillis();
        reference.set(files);
        start = true;
      }
      if (file == null) {
        root = true;
      }
      else if (files.add(file) && LOG.isTraceEnabled()) {
        LOG.debug("mark file ", file, " to update @ ", invoker);
      }
      size = files.size();
    }
    if (start) {
      LOG.debug("start collecting files to update @ ", invoker);
      invoker.invokeLater(() -> onInvokerThread(false), getUpdatingDelay());
    }
  }

  private void onInvokerThread(boolean now) {
    Set<VirtualFile> files;
    long startedAt;
    boolean fromRoot;
    boolean restart = false;
    synchronized (reference) {
      files = reference.get();
      if (files == null) {
        LOG.debug("updating queue was already flushed @ ", invoker);
        return;
      }
      startedAt = time;
      fromRoot = root;
      if (fromRoot) {
        root = false;
        reference.set(null);
      }
      else if (now || size == files.size()) {
        reference.set(null);
      }
      else {
        restart = true;
      }
    }
    if (restart) {
      LOG.debug("continue collecting files to update @ ", invoker);
      invoker.invokeLater(() -> onInvokerThread(false), getUpdatingDelay());
    }
    else {
      LOG.debug("spent ", System.currentTimeMillis() - startedAt, "ms to collect ", size, " files to update @ ", invoker);
      invoker.runOrInvokeLater(() -> updateStructure(fromRoot, files));
    }
  }

  /**
   * This method is called on invoker's thread to report changes in virtual files.
   *
   * @param fromRoot     {@code true} if roots are changed
   * @param updatedFiles a set of modified files
   */
  protected abstract void updateStructure(boolean fromRoot, @NotNull Set<? extends VirtualFile> updatedFiles);
}
