// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs;

import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.progress.Cancellation;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ex.ProjectManagerEx;
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
import com.intellij.platform.workspace.storage.impl.url.VirtualFileUrlManagerEx;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.TreeNodeProcessingResult;
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileIndex;
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileSet;
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileSetWithCustomData;
import com.intellij.workspaceModel.core.fileIndex.impl.WorkspaceFileIndexEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.io.IOError;
import java.nio.file.InvalidPathException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

final class TransientChildScanner implements ChildScanner {
  private static final Logger LOG = Logger.getInstance(TransientChildScanner.class);

  private final FSRecordsImpl vfsPeer = ((PersistentFSImpl)PersistentFS.getInstance()).peer();
  private final Consumer<VirtualFile> checkCancelled;

  TransientChildScanner(@NotNull Consumer<VirtualFile> checkCancelled) {
    this.checkCancelled = checkCancelled;
  }

  /// Traverses children of `childName` recursively, so they are loaded into VFS during event processing
  ///
  /// @return `null` if error occurred or children must not be loaded proactively. Children to load into VFS otherwise
  @Override
  public @Nullable ScannedChildren scanChildrenRecursively(@NotNull NewVirtualFile parent, @NotNull String childName) {
    try {
      NewVirtualFileSystem fs = parent.getFileSystem();
      var child = TransientVirtualFileVfsRefreshUtils.createTransientChild(parent, childName, fs);
      var exists = Cancellation.computeInNonCancelableSection(() -> child.exists());
      if (!exists || !shouldScanDirectory(parent, child)) {
        return null;
      }

      String childUrl = child.getUrl();
      var relevantExcluded = ContainerUtil.mapNotNull(ProjectManagerEx.getInstanceEx().getAllExcludedUrls(null), url -> {
        if (!VfsUtilCore.isEqualOrAncestor(childUrl, url)) return null;
        return child.findFileByRelativePath(url.substring(childUrl.length()));
      });
      return scanChildren(child, relevantExcluded, parent);
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

  private static boolean shouldScanDirectory(VirtualFile parent, VirtualFile child) {
    if (FileTypeManager.getInstance().isFileIgnored(child.getName())) return false;
    for (Project openProject : ProjectManager.getInstance().getOpenProjects()) {
      if (ReadAction.computeBlocking(() -> {
        List<WorkspaceFileSet> indexableFileSet = WorkspaceFileIndex.getInstance(openProject)
          .findFileSets(parent, true, true, /*includeContentNonIndexableSets*/ false, true, true, /*includeExternalNonIndexableSets*/ false,
                        true);
        return ContainerUtil.exists(indexableFileSet, set -> set instanceof WorkspaceFileSetWithCustomData &&
                                                             ((WorkspaceFileSetWithCustomData<?>)set).getRecursive());
      })) {
        return true;
      }
    }
    return isIndexableFilesetRecursiveRoot(child) || !indexableRootsUnder(child).isEmpty();
  }

  private static boolean isIndexableFilesetRecursiveRoot(VirtualFile file) {
    if (!RegistryManager.getInstance().is("vfs.refresh.iterate.included.files.under.exclude")) return false;

    String url = file.getUrl();
    var openProjects = ProjectManager.getInstance().getOpenProjects();
    return ContainerUtil.exists(openProjects, project -> ReadAction.computeBlocking(
      () -> WorkspaceFileIndexEx.getInstance(project).isUrlIndexableRecursiveFileSetRoot(url)));
  }

  private static @Unmodifiable List<VirtualFile> indexableRootsUnder(VirtualFile directory) {
    if (!RegistryManager.getInstance().is("vfs.refresh.iterate.included.files.under.exclude")) return Collections.emptyList();

    var roots = new LinkedHashMap<String, VirtualFile>();
    NewVirtualFileSystem fs = (NewVirtualFileSystem)directory.getFileSystem();
    String url = directory.getUrl();
    for (Project openProject : ProjectManager.getInstance().getOpenProjects()) {
      ReadAction.runBlocking(() -> {
        var workspaceFileIndex = WorkspaceFileIndexEx.getInstance(openProject);
        var vfuManager = (VirtualFileUrlManagerEx)WorkspaceModel.getInstance(openProject).getVirtualFileUrlManager();
        vfuManager.processChildrenRecursively(url, child -> {
          if (workspaceFileIndex.isUrlIndexableRecursiveFileSetRoot(child.getUrl())) {
            var root = directory.findFileByRelativePath(child.getUrl().substring(url.length()));
            if (root != null) {
              roots.putIfAbsent(root.getPath(), root);
            }
            return TreeNodeProcessingResult.SKIP_CHILDREN;
          }
          return TreeNodeProcessingResult.CONTINUE;
        });
      });
    }
    return ContainerUtil.filter(roots.values(), file -> Cancellation.computeInNonCancelableSection(() -> file.exists()));
  }

  // scan all children of "root" (except excluded dirs) recursively and return them in a ScannedChildren record
  // `null` means error during scan
  private @Nullable ScannedChildren scanChildren(VirtualFile root, List<VirtualFile> excluded, NewVirtualFile currentDir) {
    if (!root.exists() || TransientVirtualFileVfsRefreshUtils.isCached(root)) {
      return null;
    }
    int nameId = vfsPeer.getNameId("");
    ChildInfo fakeRoot = new ChildInfoImpl(nameId, null, null, null, false);
    List<ChildInfo> rootInfos = new SmartList<>(fakeRoot);

    record ScanFrame(@NotNull List<ChildInfo> parentChildren, @NotNull List<ChildInfo> children) {
    }

    VfsUtilCore.visitChildrenRecursively(root, new VirtualFileVisitor<ScanFrame>(VirtualFileVisitor.NO_FOLLOW_SYMLINKS) {
      private int checkCanceledCount;

      {
        // The visitor restores its value stack in a finally block if traversal is interrupted.
        setValueForChildren(new ScanFrame(Collections.emptyList(), rootInfos));
      }

      @Override
      public @NotNull Iterable<VirtualFile> getChildrenIterable(@NotNull VirtualFile file) {
        VirtualFile[] children = ProgressManager.getInstance().computeInNonCancelableSection(() -> file.getChildren());
        return Arrays.asList(children);
      }

      @Override
      public @NotNull Result visitFileEx(@NotNull VirtualFile file) {
        try {
          if (!file.equals(root)) {
            if ((++checkCanceledCount & 0xf) == 0) {
              checkCancelled.accept(currentDir);
            }

            ChildInfo info = createChildInfo(file);
            if (info == null) {
              return SKIP_CHILDREN;  // ignoring files deleted during the scan
            }
            getCurrentValue().children().add(info);

            FileAttributes attributes = info.getFileAttributes();
            if (attributes == null || !attributes.isDirectory() || attributes.isSymLink()) {
              return SKIP_CHILDREN;
            }
            if (SystemInfoRt.isWindows && attributes.isSpecial()) {
              return SKIP_CHILDREN;  // bypassing NTFS reparse points
            }
          }

          // on average, this "excluded" array is small for any particular root, so linear search it is.
          if (isExcluded(file, excluded) && !isIndexableFilesetRecursiveRoot(file)) {
            List<VirtualFile> contentUnderExcluded = indexableRootsUnder(file);
            if (contentUnderExcluded.isEmpty()) {
              return SKIP_CHILDREN;
            }
            // `file` is excluded but has registered content roots beneath. Replace its ChildInfo with one whose children
            // contain shared intermediate dirs down to the scanned content roots. Do not create a frame for the file because
            // `afterChildrenVisited` will not be called after skipping the children.
            List<ChildInfo> parentChildren = getCurrentValue().children();
            ChildInfo dirInfo = ContainerUtil.getLastItem(parentChildren);
            VirtualFileTreeNode rootNode = VirtualFileTreeNode.create(file, contentUnderExcluded);
            parentChildren.set(parentChildren.size() - 1, withChildrenUnderExcluded(rootNode, dirInfo, excluded, currentDir));
            return SKIP_CHILDREN;
          }

          setValueForChildren(new ScanFrame(getCurrentValue().children(), new ArrayList<>()));
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

    ChildInfo rootInfo = rootInfos.getFirst();
    ChildInfo[] children = rootInfo.getChildren();
    return children == null ? null : new ScannedChildren(children, rootInfo.isAllChildren());
  }

  private static boolean isExcluded(VirtualFile file, List<VirtualFile> excluded) {
    return excluded.contains(file);
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

  private ChildInfo withChildrenUnderExcluded(VirtualFileTreeNode node,
                                              ChildInfo info,
                                              List<VirtualFile> nestedExcluded,
                                              NewVirtualFile currentDir) {
    if (node.isContentRoot) {
      ScannedChildren children = scanChildren(node.file, nestedExcluded, currentDir);
      return children == null ? info : ((ChildInfoImpl)info).withChildren(children.children(), children.childrenComplete());
    }

    List<ChildInfo> children = new ArrayList<>(node.children.size());
    for (VirtualFileTreeNode childNode : node.children.values()) {
      ChildInfo childInfo = createChildInfo(childNode.file);
      if (childInfo == null) {
        continue;
      }
      ChildInfo childInfoWithChildren = withChildrenUnderExcluded(childNode, childInfo, nestedExcluded, currentDir);
      children.add(childInfoWithChildren);
    }
    return ((ChildInfoImpl)info).withChildren(children.toArray(ChildInfo.EMPTY_ARRAY), false);
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

      VirtualFileTreeNode node = this;
      if (node.isContentRoot) return;
      for (var child : path.reversed()) {
        node = node.children.computeIfAbsent(child.getName(), ignored -> new VirtualFileTreeNode(child));
        if (node.isContentRoot) return;
      }
      node.isContentRoot = true;
      node.children.clear();
    }
  }
}
