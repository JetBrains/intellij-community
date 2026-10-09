// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.Cancellation;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.SystemInfoRt;
import com.intellij.openapi.util.io.FileAttributes;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.util.registry.RegistryManager;
import com.intellij.openapi.vfs.DiskQueryRelay;
import com.intellij.openapi.vfs.InvalidVirtualFileAccessException;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileVisitor;
import com.intellij.openapi.vfs.newvfs.events.ChildInfo;
import com.intellij.openapi.vfs.newvfs.persistent.FSRecordsImpl;
import com.intellij.openapi.vfs.newvfs.persistent.PersistentFS;
import com.intellij.openapi.vfs.newvfs.persistent.PersistentFSImpl;
import com.intellij.platform.backend.workspace.WorkspaceModel;
import com.intellij.platform.workspace.storage.impl.url.VirtualFileUrlManagerImpl;
import com.intellij.platform.workspace.storage.url.VirtualFileUrl;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.TreeNodeProcessingResult;
import com.intellij.workspaceModel.core.fileIndex.impl.TraverseState;
import com.intellij.workspaceModel.core.fileIndex.impl.WorkspaceFileIndexEx;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;
import org.jetbrains.annotations.Unmodifiable;

import java.io.IOError;
import java.nio.file.InvalidPathException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.function.Consumer;
import java.util.function.Function;

@ApiStatus.Internal
public final class TransientChildScanner implements ChildScanner {
  private static final Logger LOG = Logger.getInstance(TransientChildScanner.class);
  private static final String ITERATE_INCLUDED_FILES_UNDER_EXCLUDE = "vfs.refresh.iterate.included.files.under.exclude";

  private static volatile @Nullable Runnable scanStartedCallbackForTests;

  private final FSRecordsImpl vfsPeer = ((PersistentFSImpl)PersistentFS.getInstance()).peer();
  private final Consumer<VirtualFile> checkCancelled;

  TransientChildScanner(@NotNull Consumer<VirtualFile> checkCancelled) {
    this.checkCancelled = checkCancelled;
  }

  /// `callback` is invoked each time the scanner starts loading children of a new directory
  @TestOnly
  public static void setScanStartedCallbackForTests(@NotNull Disposable disposable, @NotNull Runnable callback) {
    scanStartedCallbackForTests = callback;
    Disposer.register(disposable, () -> scanStartedCallbackForTests = null);
  }

  /// Traverses children of `childName` recursively, so they are loaded into VFS during event processing
  ///
  /// @return `null` if error occurred or children must not be loaded proactively. Children to load into VFS otherwise
  @Override
  public @Nullable ScannedChildren scanChildrenRecursively(@NotNull NewVirtualFile parent, @NotNull String childName) {
    try {
      NewVirtualFileSystem fs = parent.getFileSystem();
      var child = TransientVirtualFileVfsRefreshUtils.createTransientChild(parent, childName, fs);
      if (!Cancellation.computeInNonCancelableSection(() -> child.exists())) {
        return null;
      }

      return scanChildren(child, initialStates(parent, () -> checkCancelled.accept(parent)), parent);
    }
    catch (InvalidPathException e) {
      LOG.warn("Invalid child name: '" + childName + "'", e);
      return null;
    }
    catch (IOError e) {
      LOG.warn(e);
      return null;
    }
  }

  private static void notifyScanStarted() {
    Runnable callback = scanStartedCallbackForTests;
    if (callback != null) {
      callback.run();
    }
  }

  /// Using project in the application scope, may lead to [CancellationException] because project is disposed
  /// However, the vfs refresh should continue in this case if the application is not disposed
  private static <T> @Nullable T useProject(@Nullable Project project,
                                            @NotNull Runnable checkCanceled,
                                            @NotNull Function<? super Project, ? extends T> action) {
    if (project == null || project.isDisposed()) return null;
    try {
      return action.apply(project);
    }
    catch (CancellationException e) {
      checkCanceled.run();
      ProgressManager.checkCanceled();
      return null;
    }
  }

  /// [TraverseState] of a file in [#project]
  ///
  /// Project is stored in a reference, so if it is disposed, it can be released, avoiding temporary project leak
  private record ProjectState(@NotNull Ref<Project> projectRef, @NotNull TraverseState traverseState, @Nullable VirtualFileUrl vfu) {

    private <T> @Nullable T useProject(@NotNull Runnable checkCanceled, @NotNull Function<? super Project, ? extends T> action) {
      var project = projectRef.get();
      if (project != null && project.isDisposed()) {
        projectRef.set(null);
        return null;
      }
      return TransientChildScanner.useProject(project, checkCanceled, action);
    }

    /// @return `null` if the project is disposed or excludes the subtree of `file`
    @Nullable ProjectState goDown(@NotNull VirtualFile file, @NotNull Runnable checkCanceled) {
      return useProject(checkCanceled, project -> {
        VirtualFileUrl vfu = this.vfu == null ? null : this.vfu.findChild(file.getName());
        TraverseState state = this.traverseState.goDown(file, vfu, WorkspaceFileIndexEx.getInstance(project));
        if (state == null) return null;
        return new ProjectState(projectRef, state, vfu);
      });
    }
  }

  /// Whether an indexable recursive file set includes the file in any project, so the children of the file are indexable too
  private static boolean isIndexableRecursive(@NotNull List<ProjectState> states) {
    return ContainerUtil.exists(states, state -> state.traverseState().isIndexableRecursive());
  }

  /// Computes states of `parent` which is loaded into VFS for all open projects.
  /// Skips the projects which exclude the subtree of `parent`
  private static @NotNull List<ProjectState> initialStates(@NotNull VirtualFile parent, @NotNull Runnable checkCanceled) {
    String url = parent.getUrl();
    List<ProjectState> states = new ArrayList<>();
    for (Project project : ProjectManager.getInstance().getOpenProjects()) {
      var projectState = useProject(project, checkCanceled, p -> {
        TraverseState state = TraverseState.getTraverseState(parent, p);
        if (state == null) return null;
        VirtualFileUrl vfu = ((VirtualFileUrlManagerImpl)WorkspaceModel.getInstance(p).getVirtualFileUrlManager()).findNode(url);
        return new ProjectState(Ref.create(p), state, vfu);
      });
      if (projectState != null) states.add(projectState);
    }
    return states;
  }

  /// Computes states of `file` from `parentStates` of its parent.
  /// Skips the projects which exclude the subtree of `file`
  private static @NotNull List<ProjectState> goDown(@NotNull List<ProjectState> parentStates,
                                                    @NotNull VirtualFile file,
                                                    @NotNull Runnable checkCanceled) {
    if (parentStates.isEmpty()) return parentStates;
    return ContainerUtil.mapNotNull(parentStates, state -> state.goDown(file, checkCanceled));
  }

  /// Finds roots of indexable file sets under `directory` which aren't excluded in their projects
  ///
  /// @param states states of `directory`
  private static @Unmodifiable List<VirtualFile> indexableRootsUnder(VirtualFile directory,
                                                                     List<ProjectState> states,
                                                                     @NotNull Runnable checkCanceled) {
    if (!RegistryManager.getInstance().is(ITERATE_INCLUDED_FILES_UNDER_EXCLUDE)) {
      return Collections.emptyList();
    }

    var roots = new LinkedHashMap<String, VirtualFile>();
    String url = directory.getUrl();
    for (ProjectState directoryState : states) {
      directoryState.useProject(checkCanceled, project -> {
        var workspaceFileIndex = WorkspaceFileIndexEx.getInstance(project);
        var vfuManager = (VirtualFileUrlManagerImpl)WorkspaceModel.getInstance(project).getVirtualFileUrlManager();
        vfuManager.processChildrenRecursively(url, child -> {
          if (workspaceFileIndex.getFileSetsAt(child).getMask() == 0) {
            return TreeNodeProcessingResult.CONTINUE;
          }
          var root = directory.findFileByRelativePath(child.getUrl().substring(url.length()));
          if (root == null) return TreeNodeProcessingResult.SKIP_CHILDREN; // file does not exist, skip

          TraverseState state = stateUnder(directory, directoryState.traverseState(), root, child, workspaceFileIndex);
          if (state == null) return TreeNodeProcessingResult.SKIP_CHILDREN; // file is excluded, skip

          if (state.isIndexable()) {
            roots.putIfAbsent(root.getPath(), root);
            return TreeNodeProcessingResult.SKIP_CHILDREN; // find indexable file
          }
          return TreeNodeProcessingResult.CONTINUE; // file can be indexable underneath, continue
        });
        return null;
      });
    }
    return ContainerUtil.filter(roots.values(), file -> Cancellation.computeInNonCancelableSection(() -> file.exists()));
  }

  /// Computes the traverseState of `file` under `directory` from `directoryState`
  ///
  /// @param vfu the node of `file` in the [VirtualFileUrl] trie
  private static @Nullable TraverseState stateUnder(@NotNull VirtualFile directory,
                                                  @NotNull TraverseState directoryState,
                                                  @NotNull VirtualFile file,
                                                  @NotNull VirtualFileUrl vfu,
                                                  @NotNull WorkspaceFileIndexEx workspaceFileIndex) {
    List<VirtualFile> path = new ArrayList<>();
    List<VirtualFileUrl> vfus = new ArrayList<>();
    VirtualFileUrl currentVfu = vfu;
    for (VirtualFile current = file; current != null && !current.equals(directory); current = current.getParent()) {
      path.add(current);
      vfus.add(currentVfu);
      currentVfu = currentVfu == null ? null : currentVfu.getParent();
    }
    TraverseState state = directoryState;
    for (int i = path.size() - 1; i >= 0 && state != null; i--) {
      state = state.goDown(path.get(i), vfus.get(i), workspaceFileIndex);
    }
    return state;
  }

  /// Scans the indexable children of `root` recursively
  ///
  /// @param parentStates states of the parent of `root`
  /// @return `null` if an error occurs or no children need to be loaded
  private @Nullable ScannedChildren scanChildren(VirtualFile root, List<ProjectState> parentStates, NewVirtualFile currentDir) {
    if (!root.exists() || TransientVirtualFileVfsRefreshUtils.isCached(root)) {
      return null;
    }
    List<ChildInfo> rootInfos = new SmartList<>();

    record ScanFrame(@NotNull List<ChildInfo> parentChildren, @NotNull List<ChildInfo> children, @NotNull List<ProjectState> states) {
    }

    VfsUtilCore.visitChildrenRecursively(root, new VirtualFileVisitor<ScanFrame>(VirtualFileVisitor.NO_FOLLOW_SYMLINKS) {
      private int checkCanceledCount;

      {
        // The visitor restores its value stack in a finally block if traversal is interrupted.
        setValueForChildren(new ScanFrame(Collections.emptyList(), rootInfos, parentStates));
      }

      @Override
      public @NotNull Iterable<VirtualFile> getChildrenIterable(@NotNull VirtualFile file) {
        notifyScanStarted();
        VirtualFile[] children = ProgressManager.getInstance().computeInNonCancelableSection(() -> file.getChildren());
        return Arrays.asList(children);
      }

      @Override
      public @NotNull Result visitFileEx(@NotNull VirtualFile file) {
        try {
          if ((++checkCanceledCount & 0xf) == 0) {
            checkCancelled.accept(currentDir);
          }

          ChildInfo info = createChildInfo(file);
          if (info == null) {
            return SKIP_CHILDREN;
          }
          ScanFrame frame = getCurrentValue();
          List<ChildInfo> siblings = frame.children();
          siblings.add(info);

          FileAttributes attributes = info.getFileAttributes();
          if (attributes == null || !isTraversableDirectory(attributes)) {
            return SKIP_CHILDREN;
          }

          List<ProjectState> states = goDown(frame.states(), file, () -> checkCancelled.accept(currentDir));
          if (!isIndexableRecursive(states)) {
            siblings.set(siblings.size() - 1, withIndexableRootsUnder(file, info, states, currentDir));
            return SKIP_CHILDREN;
          }
          setValueForChildren(new ScanFrame(siblings, new ArrayList<>(), states));
          return CONTINUE;
        }
        catch (IOError | InvalidPathException | InvalidVirtualFileAccessException e) {
          LOG.warn(e);
          return SKIP_CHILDREN;  // ignoring exceptions from short-living temp files
        }
      }

      @Override
      public void afterChildrenVisited(@NotNull VirtualFile file) {
        ScanFrame frame = getCurrentValue();
        List<ChildInfo> parentInfos = frame.parentChildren();
        ChildInfo parentInfo = ContainerUtil.getLastItem(parentInfos);
        ChildInfo[] children = frame.children().toArray(ChildInfo.EMPTY_ARRAY);
        ChildInfo newInfo = ((ChildInfoImpl)parentInfo).withChildren(children, true);
        parentInfos.set(parentInfos.size() - 1, newInfo);
      }
    });

    ChildInfo rootInfo = ContainerUtil.getFirstItem(rootInfos);
    if (rootInfo == null) {
      return null;
    }
    ChildInfo[] children = rootInfo.getChildren();
    return children == null ? null : new ScannedChildren(children, rootInfo.isAllChildren());
  }

  /// Whether the scanner visits children of a file with `attributes`. It skips symlinks and NTFS reparse points
  private static boolean isTraversableDirectory(@NotNull FileAttributes attributes) {
    return attributes.isDirectory() && !attributes.isSymLink() && !(SystemInfoRt.isWindows && attributes.isSpecial());
  }

  private @Nullable ChildInfo createChildInfo(@NotNull VirtualFile file) {
    NewVirtualFileSystem fs = (NewVirtualFileSystem)file.getFileSystem();
    FileAttributes attributes = computeAttributesForFile(fs, file);
    if (attributes == null) {
      return null;
    }
    String symlinkTarget = attributes.isSymLink() ? fs.resolveSymLink(file) : null;
    if (symlinkTarget != null) {
      symlinkTarget = FileUtilRt.toSystemIndependentName(symlinkTarget);
    }
    int nameId = vfsPeer.getNameId(file.getName());
    return new ChildInfoImpl(nameId, attributes, null, symlinkTarget, false);
  }

  /**
   * If attributes are computed in a cancellable context, then single-thread refresh gets a performance degradation.
   * The reason is {@link DiskQueryRelay#accessDiskWithCheckCanceled(Object)},
   * which starts constant exchanging messages with an IO thread.
   * The non-cancellable section here is merely a reification of the existing implicit assumption on cancellability,
   * so it does not make anything worse.
   * In the future, it should be removed in favor of non-blocking or suspending IO.
   */
  private static @Nullable FileAttributes computeAttributesForFile(NewVirtualFileSystem fs, VirtualFile file) {
    return Cancellation.computeInNonCancelableSection(() -> {
      if (file instanceof VirtualFileWithAttributes attributesCachingVirtualFile) {
        return attributesCachingVirtualFile.getAttributes();
      }
      return fs.getAttributes(file);
    });
  }

  /// @param states states of `node.file`
  private ChildInfo withChildrenUnderExcluded(VirtualFileTreeNode node,
                                             ChildInfo info,
                                             List<ProjectState> states,
                                             NewVirtualFile currentDir) {
    List<ChildInfo> children = new ArrayList<>(node.children.size());
    for (VirtualFileTreeNode childNode : node.children.values()) {
      ChildInfo childInfo = createChildInfo(childNode.file);
      if (childInfo == null) {
        continue;
      }
      if (childNode.isContentRoot) {
        ScannedChildren scannedChildren = scanChildren(childNode.file, states, currentDir);
        children.add(scannedChildren == null
                     ? childInfo
                     : ((ChildInfoImpl)childInfo).withChildren(scannedChildren.children(), scannedChildren.childrenComplete()));
      }
      else {
        children.add(withChildrenUnderExcluded(childNode, childInfo,
                                               goDown(states, childNode.file, () -> checkCancelled.accept(currentDir)), currentDir));
      }
    }
    return ((ChildInfoImpl)info).withChildren(children.toArray(ChildInfo.EMPTY_ARRAY), false);
  }

  /// Adds to `info` the children of excluded `directory` down to the indexable roots under it
  ///
  /// @param states states of `directory`
  /// @return `info` if there are no indexable roots under `directory`
  private ChildInfo withIndexableRootsUnder(VirtualFile directory,
                                           ChildInfo info,
                                           List<ProjectState> states,
                                           NewVirtualFile currentDir) {
    List<VirtualFile> indexableRoots = indexableRootsUnder(directory, states, () -> checkCancelled.accept(currentDir));
    if (indexableRoots.isEmpty()) {
      return info;
    }
    notifyScanStarted();
    return withChildrenUnderExcluded(VirtualFileTreeNode.create(directory, indexableRoots), info, states, currentDir);
  }

  private static final class VirtualFileTreeNode {
    private final @NotNull VirtualFile file;
    private final @NotNull Map<String, VirtualFileTreeNode> children = new LinkedHashMap<>();
    private boolean isContentRoot;

    private VirtualFileTreeNode(@NotNull VirtualFile file) {
      this.file = file;
    }

    private static VirtualFileTreeNode create(VirtualFile directory, List<VirtualFile> contentRootsUnder) {
      VirtualFileTreeNode result = new VirtualFileTreeNode(directory);
      for (var contentRoot : contentRootsUnder) {
        result.addContentRoot(contentRoot);
      }
      return result;
    }

    /// Go up until reach [file], then create nodes for the passed path
    private void addContentRoot(VirtualFile contentRoot) {
      List<VirtualFile> path = new ArrayList<>();
      VirtualFile currentFile = contentRoot;
      while (!currentFile.equals(file)) {
        path.add(currentFile);
        currentFile = currentFile.getParent();
        if (currentFile == null) {
          return;
        }
      }

      if (isContentRoot) return;
      VirtualFileTreeNode node = this;
      for (var child : path.reversed()) {
        node = node.children.computeIfAbsent(child.getName(), ignored -> new VirtualFileTreeNode(child));
        if (node.isContentRoot) return;
      }
      node.isContentRoot = true;
      node.children.clear();
    }
  }
}
