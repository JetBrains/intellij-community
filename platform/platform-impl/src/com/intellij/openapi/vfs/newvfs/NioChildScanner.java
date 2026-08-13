// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs;

import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ex.ProjectManagerEx;
import com.intellij.openapi.util.SystemInfoRt;
import com.intellij.openapi.util.io.FileAttributes;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.util.registry.RegistryManager;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.newvfs.events.ChildInfo;
import com.intellij.openapi.vfs.newvfs.persistent.FSRecordsImpl;
import com.intellij.openapi.vfs.newvfs.persistent.PersistentFS;
import com.intellij.openapi.vfs.newvfs.persistent.PersistentFSImpl;
import com.intellij.platform.backend.workspace.WorkspaceModel;
import com.intellij.platform.workspace.storage.impl.url.VirtualFileUrlManagerEx;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.Stack;
import com.intellij.util.containers.TreeNodeProcessingResult;
import com.intellij.util.io.PlatformNioHelper;
import com.intellij.util.io.URLUtil;
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileIndex;
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileSet;
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileSetWithCustomData;
import com.intellij.workspaceModel.core.fileIndex.impl.WorkspaceFileIndexEx;
import com.intellij.workspaceModel.ide.VirtualFileUrlManagerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/// @deprecated Use [TransientChildScanner]
@SuppressWarnings("DeprecatedIsStillUsed")
@Deprecated
final class NioChildScanner implements ChildScanner {
  private static final Logger LOG = Logger.getInstance(NioChildScanner.class);

  private final FSRecordsImpl vfsPeer = ((PersistentFSImpl)PersistentFS.getInstance()).peer();
  private final Consumer<VirtualFile> checkCancelled;

  NioChildScanner(@NotNull Consumer<VirtualFile> checkCancelled) {
    this.checkCancelled = checkCancelled;
  }

  @Override
  public @Nullable ScannedChildren scanChildrenRecursively(@NotNull NewVirtualFile parent, @NotNull String childName) {
    if (!(parent.getFileSystem() instanceof LocalFileSystem)) {
      return null;
    }

    try {
      Path childPath = Path.of(parent.getPath(), childName);
      if (!shouldScanDirectory(parent, childPath, childName)) {
        return null;
      }

      List<Path> relevantExcluded = ContainerUtil.mapNotNull(ProjectManagerEx.getInstanceEx().getAllExcludedUrls(null), url -> {
        Path path = Path.of(VirtualFileManager.extractPath(url));
        return path.startsWith(childPath) ? path : null;
      });
      return scanChildren(childPath, relevantExcluded, parent);
    }
    catch (InvalidPathException e) {
      LOG.warn("Invalid child name: '" + childName + "'", e);
      return null;
    }
  }

  private static boolean shouldScanDirectory(VirtualFile parent, Path child, String childName) {
    if (FileTypeManager.getInstance().isFileIgnored(childName)) return false;
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

  private static boolean isIndexableFilesetRecursiveRoot(Path directoryOrFile) {
    if (!RegistryManager.getInstance().is("vfs.refresh.iterate.included.files.under.exclude")) return false;

    String url = toUrl(directoryOrFile);
    var openProjects = ProjectManager.getInstance().getOpenProjects();
    return ContainerUtil.exists(openProjects, project -> ReadAction.computeBlocking(
      () -> WorkspaceFileIndexEx.getInstance(project).isUrlIndexableRecursiveFileSetRoot(url)));
  }

  private static @Unmodifiable List<Path> indexableRootsUnder(Path directory) {
    if (!RegistryManager.getInstance().is("vfs.refresh.iterate.included.files.under.exclude")) return Collections.emptyList();

    var roots = new LinkedHashSet<Path>();
    String url = toUrl(directory);
    for (Project openProject : ProjectManager.getInstance().getOpenProjects()) {
      ReadAction.runBlocking(() -> {
        var workspaceFileIndex = WorkspaceFileIndexEx.getInstance(openProject);
        var vfuManager = (VirtualFileUrlManagerEx)WorkspaceModel.getInstance(openProject).getVirtualFileUrlManager();
        vfuManager.processChildrenRecursively(url, child -> {
          if (workspaceFileIndex.isUrlIndexableRecursiveFileSetRoot(child.getUrl())) {
            roots.add(VirtualFileUrlManagerUtil.toPath(child));
            return TreeNodeProcessingResult.SKIP_CHILDREN;
          }
          return TreeNodeProcessingResult.CONTINUE;
        });
      });
    }
    return ContainerUtil.filter(roots, Files::exists);
  }

  private static @NotNull String toUrl(Path directory) {
    return URLUtil.FILE_PROTOCOL + URLUtil.SCHEME_SEPARATOR + FileUtilRt.toSystemIndependentName(directory.toString());
  }

  // scan all children of "root" (except excluded dirs) recursively and return them in a ScannedChildren record
  // `null` means error during scan
  private @Nullable ScannedChildren scanChildren(Path root, List<Path> excluded, NewVirtualFile currentDir) {
    // the stack contains a list of children found so far in the current directory
    Stack<List<ChildInfo>> stack = new Stack<>();
    int nameId = vfsPeer.getNameId("");
    ChildInfo fakeRoot = new ChildInfoImpl(nameId, null, null, null, false);
    stack.push(new SmartList<>(fakeRoot));
    /*
    1. (if file) `visitFile`: on top of the stack lays a list of children of the parent directory.
    Add fileInfo of the current file to the list. Skip steps 2-4

    1. (if directory) `preVisitDirectory`
      calls `visitFile` (same as for a file)
      a) (if not excluded) push an empty list on the stack if the directory
      b) (if excluded) skip steps 2-4
      c) (if excluded but has included files underneath) collect info about those includes.
      Add this info into the last childInfo in the list on top of the stack (this child info is placed in `visitFile`)

    2. `visitFile`: append a fileInfo to the list on top of the stack
    3. (if directory) visit children, perform steps 1-4 on each of them

    4. (if directory) `postVisitDirectory`
    At this point on top of the stack is a list of children of the current directory.
    Underneath is a list of children of the parent directory. At the end of this list is our directory info from step 2 without children.
    Add children to this info
     */
    FileVisitor<Path> visitor = new SimpleFileVisitor<>() {
      private int checkCanceledCount;

      @Override
      public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
        if (!dir.equals(root)) {
          visitFile(dir, attrs);
        }
        if (SystemInfoRt.isWindows && attrs.isOther()) {
          return FileVisitResult.SKIP_SUBTREE;  // bypassing NTFS reparse points
        }
        // on average, this "excluded" array is small for any particular root, so linear search it is.
        if (excluded.contains(dir) && !isIndexableFilesetRecursiveRoot(dir)) {
          List<Path> contentUnderExcluded = indexableRootsUnder(dir);
          if (contentUnderExcluded.isEmpty()) {
            return FileVisitResult.SKIP_SUBTREE;
          }
          // `dir` is excluded but has registered content roots beneath. Replace the ChildInfo for `dir`
          // (recorded by `visitFile(dir, attrs)` above when `dir != root`, or the pre-seeded fakeRoot otherwise)
          // with one whose children contain shared intermediate dirs down to the scanned content roots.
          // We do not push to the stack because `postVisitDirectory` will not fire after `SKIP_SUBTREE`.
          List<ChildInfo> parentChildren = stack.peek();
          ChildInfo dirInfo = ContainerUtil.getLastItem(parentChildren);
          PathTreeNode rootPathNode = new PathTreeNode(dir, contentUnderExcluded);
          parentChildren.set(parentChildren.size() - 1, withChildrenUnderExcluded(rootPathNode, dirInfo, excluded, currentDir));
          return FileVisitResult.SKIP_SUBTREE;
        }
        stack.push(new ArrayList<>());
        return FileVisitResult.CONTINUE;
      }

      @Override
      public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
        if ((++checkCanceledCount & 0xf) == 0) {
          checkCancelled.accept(currentDir);
        }
        stack.peek().add(createChildInfo(file, attrs));
        return FileVisitResult.CONTINUE;
      }

      @Override
      public FileVisitResult postVisitDirectory(Path dir, IOException exc) {
        List<ChildInfo> childInfos = stack.pop();
        List<ChildInfo> parentInfos = stack.peek();
        ChildInfo parentInfo = ContainerUtil.getLastItem(parentInfos);
        ChildInfo[] children = childInfos.toArray(ChildInfo.EMPTY_ARRAY);
        ChildInfo newInfo = ((ChildInfoImpl)parentInfo).withChildren(children, true);
        parentInfos.set(parentInfos.size() - 1, newInfo);
        return FileVisitResult.CONTINUE;
      }

      @Override
      public FileVisitResult visitFileFailed(Path file, IOException exc) {
        return FileVisitResult.CONTINUE;  // ignoring exceptions from short-living temp files
      }
    };

    try {
      PlatformNioHelper.walkFileTree(root, visitor);
    }
    catch (IOException e) {
      LOG.warn(e);
      return null;  // tell the client we didn't find any children, abandon the optimization altogether
    }

    ChildInfo rootInfo = stack.pop().getFirst();
    ChildInfo[] children = rootInfo.getChildren();
    return children == null ? null : new ScannedChildren(children, rootInfo.isAllChildren());
  }

  private ChildInfo readChildInfo(Path file) throws IOException {
    BasicFileAttributes attrs = Files.readAttributes(file, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
    return createChildInfo(file, attrs);
  }

  private ChildInfo createChildInfo(Path file, BasicFileAttributes attrs) throws IOException {
    FileAttributes attributes = FileAttributes.fromNio(file, attrs);
    String symLinkTarget = attrs.isSymbolicLink() ? FileUtilRt.toSystemIndependentName(file.toRealPath().toString()) : null;
    int nameId = vfsPeer.getNameId(file.getFileName().toString());
    return new ChildInfoImpl(nameId, attributes, null, symLinkTarget, false);
  }

  private ChildInfo withChildrenUnderExcluded(PathTreeNode node, ChildInfo info, List<Path> nestedExcluded, NewVirtualFile currentDir) {
    if (node.isContentRoot) {
      ScannedChildren children = scanChildren(node.path, nestedExcluded, currentDir);
      return children == null ? info : ((ChildInfoImpl)info).withChildren(children.children(), children.childrenComplete());
    }

    List<ChildInfo> children = new ArrayList<>(node.children.size());
    for (PathTreeNode childNode : node.children.values()) {
      try {
        ChildInfo childInfo = readChildInfo(childNode.path);
        ChildInfo childInfoWithChildren = withChildrenUnderExcluded(childNode, childInfo, nestedExcluded, currentDir);
        children.add(childInfoWithChildren);
      }
      catch (IOException e) {
        LOG.warn(e);
      }
    }
    return ((ChildInfoImpl)info).withChildren(children.toArray(ChildInfo.EMPTY_ARRAY), false);
  }

  private static final class PathTreeNode {
    private final Path path;
    private final Map<Path, PathTreeNode> children = new LinkedHashMap<>();
    private boolean isContentRoot;

    private PathTreeNode(Path path) {
      this.path = path;
    }

    private PathTreeNode(Path dir, List<Path> contentRootsUnder) {
      this(dir);
      for (var root : contentRootsUnder) {
        addContentRoot(dir.relativize(root));
      }
    }

    private void addContentRoot(Path relativePath) {
      if (relativePath.toString().isEmpty()) {
        isContentRoot = true;
        children.clear();
        return;
      }

      PathTreeNode node = this;
      for (Path path : relativePath) {
        Path childPath = node.path.resolve(path);
        node = node.children.computeIfAbsent(childPath, child -> new PathTreeNode(child));
        if (node.isContentRoot) return;
      }
      node.isContentRoot = true;
      node.children.clear();
    }
  }
}
