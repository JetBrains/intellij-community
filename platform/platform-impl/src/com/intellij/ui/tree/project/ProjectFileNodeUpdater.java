// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.tree.project;

import com.intellij.ide.scratch.RootType;
import com.intellij.ide.ui.VirtualFileAppearanceListener;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.AdditionalLibraryRootsListener;
import com.intellij.openapi.roots.ModuleRootEvent;
import com.intellij.openapi.roots.ModuleRootListener;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.BulkFileListener;
import com.intellij.openapi.vfs.newvfs.events.VFileCopyEvent;
import com.intellij.openapi.vfs.newvfs.events.VFileCreateEvent;
import com.intellij.openapi.vfs.newvfs.events.VFileDeleteEvent;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import com.intellij.openapi.vfs.newvfs.events.VFileMoveEvent;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiTreeChangeAdapter;
import com.intellij.psi.PsiTreeChangeEvent;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.ui.treeStructure.ProjectViewUpdateCause;
import com.intellij.util.concurrency.EdtExecutorService;
import com.intellij.util.concurrency.Invoker;
import com.intellij.util.containers.SmartHashSet;
import com.intellij.util.messages.MessageBusConnection;
import kotlinx.coroutines.CoroutineScope;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jspecify.annotations.NonNull;

import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static com.intellij.openapi.vfs.VirtualFileManager.VFS_CHANGES;
import static com.intellij.psi.util.PsiUtilCore.getVirtualFile;
import static com.intellij.ui.tree.project.ProjectViewUpdateCauseUtilKt.guessProjectViewUpdateCauseByCaller;

public abstract class ProjectFileNodeUpdater {
  private static final Logger LOG = Logger.getInstance(ProjectFileNodeUpdater.class);
  private final Ref<Set<VirtualFile>> reference = new Ref<>();
  private final ProjectFileNodeUpdaterInvoker invoker;
  private volatile boolean root;
  private volatile long time;
  private volatile int size;
  private final Set<ProjectViewUpdateCause> updateFromRootCauses = ConcurrentHashMap.newKeySet();
  private final Set<ProjectViewUpdateCause> updateByFileCauses = ConcurrentHashMap.newKeySet();

  public ProjectFileNodeUpdater(@NotNull Project project, @NotNull Invoker invoker) {
    this(project, new ProjectFileNodeUpdaterLegacyInvoker(invoker));
  }

  public ProjectFileNodeUpdater(@NotNull Project project, @NotNull CoroutineScope coroutineScope) {
    this(project, new ProjectFileNodeUpdaterCoroutineInvoker(coroutineScope));
  }

  private ProjectFileNodeUpdater(@NotNull Project project, @NotNull ProjectFileNodeUpdaterInvoker invoker) {
    this.invoker = invoker;
    MessageBusConnection connection = project.getMessageBus().connect(invoker);
    connection.subscribe(ModuleRootListener.TOPIC, new ModuleRootListener() {
      @Override
      public void rootsChanged(@NotNull ModuleRootEvent event) {
        updateFromRoot(ProjectViewUpdateCause.ROOTS_MODULE);
      }
    });
    connection.subscribe(
      AdditionalLibraryRootsListener.TOPIC,
      (presentableLibraryName, oldRoots, newRoots, libraryNameForDebug) ->
        updateFromRoot(ProjectViewUpdateCause.ROOTS_LIBRARY)
    );
    connection.subscribe(VFS_CHANGES, new BulkFileListener() {
      @Override
      public void after(@NotNull List<? extends @NotNull VFileEvent> events) {
        for (VFileEvent event : events) {
          if (event instanceof VFileCreateEvent create) {
            updateFromFile(create.getParent(), ProjectViewUpdateCause.VFS_CREATE);
          }
          else if (event instanceof VFileCopyEvent copy) {
            updateFromFile(copy.getNewParent(), ProjectViewUpdateCause.VFS_COPY);
          }
          else if (event instanceof VFileMoveEvent move) {
            updateFromFile(move.getNewParent(), ProjectViewUpdateCause.VFS_MOVE);
            updateFromFile(move.getOldParent(), ProjectViewUpdateCause.VFS_MOVE);
            updateFromFile(move.getFile(), ProjectViewUpdateCause.VFS_MOVE);
          }
          else {
            VirtualFile file = event.getFile();
            if (file != null) {
              if (event instanceof VFileDeleteEvent) {
                VirtualFile parent = file.getParent();
                if (parent != null) updateFromFile(parent, ProjectViewUpdateCause.VFS_DELETE);
              }
              updateFromFile(file, ProjectViewUpdateCause.VFS);
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
        updateFromElement(event.getParent(), ProjectViewUpdateCause.PSI_CHILDREN);
      }

      @Override
      public void childMoved(@NotNull PsiTreeChangeEvent event) {
        updateFromElement(event.getOldParent(), ProjectViewUpdateCause.PSI_MOVE);
        updateFromElement(event.getNewParent(), ProjectViewUpdateCause.PSI_MOVE);
      }
    }, invoker);
    RootType.ROOT_EP.addChangeListener(() -> updateFromRoot(ProjectViewUpdateCause.ROOTS_EP), project);
    connection.subscribe(VirtualFileAppearanceListener.TOPIC, new VirtualFileAppearanceListener() {
      @Override
      public void virtualFileAppearanceChanged(@NotNull VirtualFile virtualFile) {
        updateFromFile(virtualFile, ProjectViewUpdateCause.FILE_APPEARANCE);
      }
    });
  }

  /**
   * Notifies that project roots are changed.
   * <p>
   * The {@link #onInvokerThread} method will be executed with a small delay
   * after calling of this method.
   * </p>
   * <p>
   *   This method is for plugin developers only. For internal use, call {@link #updateFromRoot(ProjectViewUpdateCause)}
   *   and specify the update cause explicitly.
   * </p>
   *
   * @see #getUpdatingDelay
   */
  public void updateFromRoot() {
    updateFromRootCauses.add(guessProjectViewUpdateCauseByCaller(ProjectFileNodeUpdater.class));
    updateLater(null);
  }

  @ApiStatus.Internal
  public void updateFromRoot(@NotNull ProjectViewUpdateCause cause) {
    updateFromRootCauses.add(cause);
    updateLater(null);
  }

  /**
   * Notifies that the specified file (or folder) is changed.
   * <p>
   * The {@link #onInvokerThread} method will be executed with a small delay
   * after last calling of this method,
   * i.e. a bunch of modified files will be reported together.
   * </p>
   * <p>
   *   This method is for plugin developers only. For internal use, call {@link #updateFromFile(VirtualFile, ProjectViewUpdateCause)}
   *   and specify the update cause explicitly.
   * </p>
   *
   * @param file a modified virtual file
   * @see #getUpdatingDelay
   */
  public void updateFromFile(@Nullable VirtualFile file) {
    if (file != null) {
      updateByFileCauses.add(guessProjectViewUpdateCauseByCaller(ProjectFileNodeUpdater.class));
      updateLater(file);
    }
  }

  @ApiStatus.Internal
  public void updateFromFile(@Nullable VirtualFile file, @NotNull ProjectViewUpdateCause cause) {
    if (file != null) {
      updateByFileCauses.add(cause);
      updateLater(file);
    }
  }

  /**
   * Notifies that the specified PSI element is changed.
   * <p>
   * If this element corresponds to a virtual file,
   * it will be marked as changed.
   * </p>
   * <p>
   *   This method is for plugin developers only. For internal use, call {@link #updateFromElement(PsiElement, ProjectViewUpdateCause)}
   *   and specify the update cause explicitly.
   * </p>
   *
   * @param element a modified PSI element
   * @see #updateFromFile
   */
  public void updateFromElement(@Nullable PsiElement element) {
    updateFromFile(getVirtualFile(element));
  }

  @ApiStatus.Internal
  public void updateFromElement(@Nullable PsiElement element, @NotNull ProjectViewUpdateCause cause) {
    updateFromFile(getVirtualFile(element), cause);
  }

  /**
   * Notifies that all collected files should be reported as soon as possible.
   * Usually, it is needed to find an added file in a tree right after adding.
   */
  public void updateImmediately(@NotNull Runnable onDone) {
    invoker.invoke(() -> onInvokerThread(true)).onProcessed(o -> EdtExecutorService.getInstance().execute(onDone));
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
      invoker.invoke(() -> updateStructure(fromRoot, files));
    }
  }

  @ApiStatus.Internal
  protected Collection<ProjectViewUpdateCause> getAndClearUpdateFromRootCauses() {
    return getAndClear(updateFromRootCauses);
  }

  @ApiStatus.Internal
  protected Collection<ProjectViewUpdateCause> getAndClearUpdateByFileCauses() {
    return getAndClear(updateByFileCauses);
  }

  private static @NonNull HashSet<ProjectViewUpdateCause> getAndClear(Set<ProjectViewUpdateCause> causes) {
    var result = new HashSet<ProjectViewUpdateCause>();
    // We're not very interested in consistency here, as it's for statistics only.
    // But it's still nice not to miss a value in the case it's added a moment after this call.
    // So instead of copy-then-clear, we copy and remove values one-by-one,
    // ensuring that only the values we have read are removed.
    for (Iterator<ProjectViewUpdateCause> iterator = causes.iterator(); iterator.hasNext(); ) {
      ProjectViewUpdateCause cause = iterator.next();
      result.add(cause);
      iterator.remove();
    }
    return result;
  }

  /**
   * This method is called on invoker's thread to report changes in virtual files.
   *
   * @param fromRoot     {@code true} if roots are changed
   * @param updatedFiles a set of modified files
   */
  protected abstract void updateStructure(boolean fromRoot, @NotNull Set<? extends VirtualFile> updatedFiles);
}
