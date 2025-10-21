// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.impl;

import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.util.text.StringUtilRt;
import com.intellij.openapi.vfs.*;
import com.intellij.openapi.vfs.impl.VirtualFilePointerManagerImpl.NodeToUpdate;
import com.intellij.openapi.vfs.newvfs.ArchiveFileSystem;
import com.intellij.openapi.vfs.newvfs.ManagingFS;
import com.intellij.openapi.vfs.newvfs.NewVirtualFile;
import com.intellij.openapi.vfs.newvfs.NewVirtualFileSystem;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import com.intellij.openapi.vfs.newvfs.impl.NullVirtualFile;
import com.intellij.openapi.vfs.newvfs.impl.VirtualFileSystemEntry;
import com.intellij.openapi.vfs.pointers.VirtualFilePointerListener;
import com.intellij.util.ArrayUtil;
import com.intellij.util.PathUtilRt;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import com.intellij.util.io.URLUtil;
import org.jetbrains.annotations.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@ApiStatus.Internal
public final class FilePartNodeRoot extends FilePartNode {
  private FilePartNodeRoot(@NotNull NewVirtualFileSystem fs) {
    super(fs);
  }

  @Override
  public String toString() {
    return "root -> "+children.length;
  }

  @NotNull
  @Override
  CharSequence getName() {
    return "";
  }

  @NotNull
  NodeToUpdate findOrCreateByFile(@NotNull VirtualFile file) {
    int nameId = getNameId(file);
    NewVirtualFileSystem fs = (NewVirtualFileSystem)file.getFileSystem();
    VirtualFile parent = getParentThroughJar(file, fs);
    return matchById(parent, file, nameId, new MultiMap<>(), true, true, fs);
  }

  /**
   * Appends to {@code out} all nodes under this node whose path (beginning from this node) starts with the given path
   * ({@code parent.getPath() + "/" + getVFileName(childNameId)}) and all nodes under this node with recursive directory pointers whose
   * path is ancestor of the given path.
   */
  void addRelevantPointersFrom(@NotNull VirtualFileSystemEntry parent,
                               @Nullable VirtualFile file,
                               int childNameId,
                               @NotNull MultiMap<? super VirtualFilePointerListener, ? super VirtualFilePointerImpl> toFirePointers,
                               @NotNull List<? super NodeToUpdate> toUpdateNodes,
                               boolean addSubdirectoryPointers,
                               @NotNull NewVirtualFileSystem fs,
                               boolean addRecursiveDirectoryPointers,
                               @NotNull VFileEvent event) {
    NodeToUpdate toUpdate = matchById(parent, file, childNameId, toFirePointers, false, addRecursiveDirectoryPointers, fs);
    if (toUpdate != null) {
      toUpdate.myEvent = event;
      toUpdateNodes.add(toUpdate);
      toUpdate.node.processPointers(pointer -> toFirePointers.putValue(pointer.myListener, pointer));
      if (addSubdirectoryPointers) {
        // when "a/b" changed, treat all "a/b/*" virtual file pointers as changed because that's what happens on directory rename "a"->"newA": "a" deleted and "newA" created
        addAllPointersStrictlyUnder(toUpdate.node, toFirePointers);
      }
    }
  }

  private static void addAllPointersStrictlyUnder(@NotNull FilePartNode node,
                                                  @NotNull MultiMap<? super VirtualFilePointerListener, ? super VirtualFilePointerImpl> toFirePointers) {
    for (FilePartNode child : node.children) {
      child.processPointers(pointer -> toFirePointers.putValue(pointer.myListener, pointer));
      addAllPointersStrictlyUnder(child, toFirePointers);
    }
  }

  /**
   * Tries to match the given path (parent, childNameId) with the trie structure of FilePartNodes
   * <p>Recursive nodes (i.e. the nodes containing VFP with recursive==true) will be added to outDirs.
   */
  @Contract("_, _, _, _, true, _, _ -> !null")
  private NodeToUpdate matchById(@Nullable VirtualFile parent,
                                 @Nullable VirtualFile file,
                                 int childNameId,
                                 @NotNull MultiMap<? super VirtualFilePointerListener, ? super VirtualFilePointerImpl> toFirePointers,
                                 boolean createIfNotFound,
                                 boolean addRecursiveDirectoryPtr,
                                 @NotNull NewVirtualFileSystem childFs) {
    if (childNameId <= 0 && childNameId != JAR_SEPARATOR_NAME_ID) throw new IllegalArgumentException("invalid argument childNameId: " + childNameId);
    if (file != null) {
      VirtualFileSystem fsFromFile = file.getFileSystem();
      assert childFs == fsFromFile : "fs=" + childFs + "; file.fs=" + fsFromFile+"; parent="+parent+"; file="+file;
    }
    List<VirtualFile> hierarchy = parent == null ? Collections.emptyList() : getHierarchy(parent);
    FilePartNode node = this;
    for (int i = hierarchy.size() - 1; i >= 0; i--) {
      VirtualFile part = hierarchy.get(i);
      int nameId = getNameId(part);
      FilePartNode child = node.findChildByNameId(part, nameId, createIfNotFound, (NewVirtualFileSystem)part.getFileSystem());
      if (child == null) return null;
      if (child instanceof UrlPartNode) {
        // by some strange accident there is UrlPartNode when the corresponding file is alive and kicking - replace with proper FPPN
        child = child.replaceWithFPPN(part, node);
      }
      if (addRecursiveDirectoryPtr) {
        // recursive pointers must be fired even for events deep under them
        child.addRecursiveDirectoryPtrTo(toFirePointers);
      }
      node = child;
    }

    FilePartNode child = node.findChildByNameId(file, childNameId, createIfNotFound, childFs);
    return child == null ? null : new NodeToUpdate(node, child);
  }

  private static @NotNull List<VirtualFile> getHierarchy(@NotNull VirtualFile file) {
    NewVirtualFileSystem fs = (NewVirtualFileSystem)file.getFileSystem();
    List<VirtualFile> result = new ArrayList<>();
    while (true) {
      result.add(file);
      file = getParentThroughJar(file, fs);
      if (file == null) break;
      fs = (NewVirtualFileSystem)file.getFileSystem();
    }
    return result;
  }

  /** Find a pointer to the given path, taking into account case-sensitivity of fs, or create a new pointer, if existing not found */
  @NotNull
  NodeToUpdate findOrCreateByPath(@NotNull String path, @NotNull NewVirtualFileSystem fs) {
    NewVirtualFileSystem currentFS;
    String relativePathInsideJar;
    if (fs instanceof ArchiveFileSystem) {
      currentFS = LocalFileSystem.getInstance();
      int i = path.lastIndexOf(JarFileSystem.JAR_SEPARATOR);
      // strip everything after "!/" and after extractRootFromPath() append it back,
      // because LocalVirtualFileSystem.normalize() is afraid of these jar separators and tries to absolutize them incorrectly (e.g. "C:!/foo" -> "C:idea/bin/!/foo")
      if (i == -1) {
        relativePathInsideJar = JarFileSystem.JAR_SEPARATOR;
      }
      else {
        relativePathInsideJar = path.substring(i);
        path = path.substring(0, i);
      }
    }
    else {
      currentFS = fs;
      relativePathInsideJar = "";
    }
    NewVirtualFileSystem.PathFromRoot pair = NewVirtualFileSystem.extractRootFromPath(currentFS, path);
    String pathFromRoot = pair == null ? path : pair.pathFromRoot();
    pathFromRoot += relativePathInsideJar;
    List<String> names = splitNames(pathFromRoot);
    NewVirtualFile fsRoot = pair == null ? null : pair.root();

    FilePartNode currentNode = this;
    FilePartNode parentNode = this;

    if (fsRoot != null) {
      FilePartNode child = new FilePartNode(getNameId(fsRoot), fsRoot, currentFS);
      int index = binarySearchChildByName(fsRoot.getNameSequence());
      if (index >= 0) {
        currentNode = currentNode.children[index];
      }
      else {
        currentNode.children = ArrayUtil.insert(currentNode.children, -index - 1, child);
        if (currentNode.children.length >= 2) {
          // it's expected that there won't be a lot of roots, so sorting should be cheap
          Arrays.sort(currentNode.children, (c1, c2) -> StringUtil.compare(c1.getName(), c2.getName(), !isCaseSensitive()));
        }
        currentNode = child;
      }
    }

    return trieDescend(fs, currentFS, names, fsRoot, currentNode, parentNode);
  }

  // extracted private method to split code which is too large for JDK17 to not crash (see IDEA-289921 [JBR17] Constant crashes while executing tests on TeamCity
  private static @NotNull NodeToUpdate trieDescend(@NotNull NewVirtualFileSystem fs,
                                          @NotNull NewVirtualFileSystem currentFS,
                                          @NotNull List<String> names,
                                          @Nullable NewVirtualFile fsRoot,
                                          @NotNull FilePartNode currentNode,
                                          @NotNull FilePartNode parentNode) {
    VirtualFile NEVER_TRIED_TO_FIND = NullVirtualFile.INSTANCE;
    // we try to never call file.findChild() until absolutely necessary, because it's expensive;
    // instead, rely on string name matching as long as possible
    VirtualFile currentFile = NEVER_TRIED_TO_FIND;
    for (int i = names.size() - 1; i >= 0; i--) {
      String name = names.get(i);
      currentFS = enterJar(fs, currentFS, name);

      int index = currentNode.binarySearchChildByName(name);
      if (index >= 0) {
        parentNode = currentNode;
        currentNode = currentNode.children[index];
        //noinspection UseVirtualFileEquals
        currentFile = currentFile == NEVER_TRIED_TO_FIND || currentFile == null ? currentFile : currentFile.findChild(name);
        continue;
      }

      // create and insert new node
      // first, have to check if the file root/names(end)/.../names[i] exists
      // if yes, create nameId-based FilePartNode (for faster search and memory efficiency),
      // if not, create temp UrlPartNode which will be replaced with FPPN when the real file is created
      //noinspection UseVirtualFileEquals
      if (currentFile == NEVER_TRIED_TO_FIND) {
        fsRoot = findRoot(fs, names, fsRoot);
        currentFile = fsRoot == null ? null : findFileFromRoot(fsRoot, currentFS, names, i);
      }
      else {
        currentFile = currentFile == null ? null : findChildThroughJar(currentFile, name, currentFS);
      }

      // check that found child name is the same as the one we were looking for
      // otherwise it may be the 8.3 abbreviation, which we should expand and look again
      //noinspection UseVirtualFileEquals
      if (currentFile != null && currentFile != NEVER_TRIED_TO_FIND && !currentFile.getName().equals(name)) {
        name = currentFile.getName();
        index = currentNode.binarySearchChildByName(name);
        if (index >= 0) {
          parentNode = currentNode;
          currentNode = currentNode.children[index];
          continue;
        }
      }

      FilePartNode child = createNode(currentFS, currentNode, currentFile, name);

      currentNode.children = ArrayUtil.insert(currentNode.children, -index - 1, child);
      parentNode = currentNode;
      currentNode = child;
    }
    return new NodeToUpdate(parentNode, currentNode);
  }

  private static @NotNull FilePartNode createNode(@NotNull NewVirtualFileSystem currentFS,
                                                  @NotNull FilePartNode currentNode,
                                                  @Nullable VirtualFile currentFile,
                                                  @NotNull String name) {
    if (currentFile == null) {
      return new UrlPartNode(name, urlOf(currentNode.fileOrUrl), currentFS);
    }
    int nameId = name.equals(JarFileSystem.JAR_SEPARATOR) ? JAR_SEPARATOR_NAME_ID : getNameId(currentFile);
    return new FilePartNode(nameId, currentFile, currentFS);
  }

  private static @Nullable NewVirtualFile findRoot(@NotNull NewVirtualFileSystem fs, @NotNull List<String> names, @Nullable NewVirtualFile fsRoot) {
    if (fsRoot == null) {
      String rootName = ContainerUtil.getLastItem(names);
      fsRoot = ManagingFS.getInstance().findRoot(rootName, fs instanceof ArchiveFileSystem ? LocalFileSystem.getInstance() : fs);
      if (fsRoot != null && !StringUtilRt.equal(fsRoot.getName(), rootName, fsRoot.isCaseSensitive())) {
        // ignore really weird root names, like "/" under windows
        fsRoot = null;
      }
    }
    return fsRoot;
  }

  private static NewVirtualFileSystem enterJar(@NotNull NewVirtualFileSystem fs, @NotNull NewVirtualFileSystem currentFS, @NotNull String name) {
    if (name.equals(JarFileSystem.JAR_SEPARATOR) && currentFS instanceof LocalFileSystem) {
      // switch inside jar
      currentFS = fs;
    }
    return currentFS;
  }

  static @NotNull List<String> splitNames(@NotNull String path) {
    int end = path.length();
    if (end == 0) return Collections.emptyList();
    List<String> names = new ArrayList<>(Math.max(20, end/4)); // path length -> path height approximation
    while (true) {
      boolean isJarSeparator = StringUtil.endsWith(path, 0, end, JarFileSystem.JAR_SEPARATOR)
                               && (end == 2 && SystemInfo.isWindows || end > 2 && path.charAt(end - 3) != '/');
      if (isJarSeparator) {
        names.add(JarFileSystem.JAR_SEPARATOR);
        end -= 2;
      }
      if (end != 0 && path.charAt(end-1) == '/') {
        end--;
      }
      if (end == 0) {
        break; // here's separator between non-empty root (e.g. on Windows) and path's tail
      }
      int startIndex = extractName(path, end);
      assert startIndex != end : "startIndex: "+startIndex+"; end: "+end+"; path:'"+path+"'; toExtract: '"+path.substring(0, end)+"'";
      names.add(path.substring(startIndex, end));
      if (startIndex == 0) {
        break;
      }

      end = startIndex;
    }
    return names;
  }

  private static VirtualFile findFileFromRoot(@NotNull NewVirtualFile root,
                                              @NotNull NewVirtualFileSystem fs,
                                              @NotNull List<String> names,
                                              int startIndex) {
    VirtualFile file = root;
    for (int i = names.size() - 1; i >= startIndex; i--) {
      String name = names.get(i);
      file = findChildThroughJar(file, name, fs);
      if (file == null) break;
    }
    return file;
  }

  // returns start index of the name (i.e. path[return..length) is considered a name)
  private static int extractName(@NotNull CharSequence path, int endOffset) {
    int i = StringUtil.lastIndexOf(path, '/', 0, endOffset);
    if (i != -1 && PathUtilRt.isWindowsUNCRoot(path, i)) {
      // UNC
      return 0;
    }
    return i + 1;
  }

  void removePointer(@NotNull VirtualFilePointerImpl pointer) {
    FilePartNode node = pointer.myNode;
    int remainingLeaves = node.removeLeaf(pointer);
    if (remainingLeaves == 0) {
      VirtualFile file = fileOrNull(node.fileOrUrl);
      if (file == null) {
        removeEmptyNodesByPath(VfsUtilCore.urlToPath(urlOf(node.fileOrUrl)));
      }
      else {
        List<VirtualFile> parts = getHierarchy(file);
        removeEmptyNodesByFile(parts);
      }
    }
  }

  void checkConsistency() {
    if (VirtualFilePointerManagerImpl.shouldCheckConsistency()) {
      doCheckConsistency(null, "", fs.getProtocol() + URLUtil.SCHEME_SEPARATOR);
    }
  }

  @VisibleForTesting
  public static @NotNull FilePartNodeRoot createFakeRoot(@NotNull NewVirtualFileSystem fs) {
    return new FilePartNodeRoot(fs);
  }
}
