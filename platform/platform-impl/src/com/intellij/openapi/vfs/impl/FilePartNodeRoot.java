// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vfs.impl;

import com.intellij.openapi.application.impl.ApplicationInfoImpl;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.impl.VirtualFilePointerManagerImpl.NodeToUpdate;
import com.intellij.openapi.vfs.newvfs.ArchiveFileSystem;
import com.intellij.openapi.vfs.newvfs.ManagingFS;
import com.intellij.openapi.vfs.newvfs.NewVirtualFile;
import com.intellij.openapi.vfs.newvfs.NewVirtualFileSystem;
import com.intellij.openapi.vfs.newvfs.impl.NullVirtualFile;
import com.intellij.openapi.vfs.newvfs.impl.VirtualFileSystemEntry;
import com.intellij.openapi.vfs.pointers.VirtualFilePointerListener;
import com.intellij.util.ArrayUtil;
import com.intellij.util.PathUtilRt;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import com.intellij.util.io.URLUtil;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class FilePartNodeRoot extends FilePartNode {
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
    return matchById(parent, file, nameId, new MultiMap<>(), true, fs);
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
                               @NotNull NewVirtualFileSystem fs) {
    if (childNameId <= 0) throw new IllegalArgumentException("invalid argument childNameId: "+childNameId);
    NodeToUpdate toUpdate = matchById(parent, file, childNameId, toFirePointers, false, fs);
    if (toUpdate != null) {
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
  @Contract("_, _, _, _, true, _ -> !null")
  private NodeToUpdate matchById(@Nullable VirtualFile parent,
                                 @Nullable VirtualFile file,
                                 int childNameId,
                                 @NotNull MultiMap<? super VirtualFilePointerListener, ? super VirtualFilePointerImpl> toFirePointers,
                                 boolean createIfNotFound,
                                 @NotNull NewVirtualFileSystem fs) {
    if (childNameId <= 0 && childNameId != JAR_SEPARATOR_NAME_ID) throw new IllegalArgumentException("invalid argument childNameId: " + childNameId);
    List<VirtualFile> hierarchy = parent == null ? Collections.emptyList() : getHierarchy(parent, fs);
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
      // recursive pointers must be fired even for events deep under them
      child.addRecursiveDirectoryPtrTo(toFirePointers);
      node = child;
    }

    FilePartNode child = node.findChildByNameId(file, childNameId, createIfNotFound, fs);
    return child == null ? null : new NodeToUpdate(node, child);
  }

  private static @NotNull List<VirtualFile> getHierarchy(@NotNull VirtualFile file, @NotNull NewVirtualFileSystem fs) {
    List<VirtualFile> result = new ArrayList<>();
    while (true) {
      result.add(file);
      file = getParentThroughJar(file, fs);
      if (file == null) break;
      fs = (NewVirtualFileSystem)file.getFileSystem();
    }
    return result;
  }

  @NotNull
  NodeToUpdate findOrCreateByPath(@NotNull String path, @NotNull NewVirtualFileSystem fs) {
    List<String> names = splitNames(path);
    NewVirtualFile fsRoot = null;

    VirtualFile NEVER_TRIED_TO_FIND = NullVirtualFile.INSTANCE;
    // we try to never call file.findChild() because it's expensive
    VirtualFile currentFile = NEVER_TRIED_TO_FIND;
    FilePartNode currentNode = this;
    FilePartNode parentNode = this;
    NewVirtualFileSystem currentFS = fs instanceof ArchiveFileSystem ? LocalFileSystem.getInstance() : fs;
    for (int i = names.size() - 1; i >= 0; i--) {
      String name = names.get(i);
      if (name.equals(JarFileSystem.JAR_SEPARATOR) && currentFS instanceof LocalFileSystem) {
        // switch inside jar
        currentFS = fs;
      }
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
        if (fsRoot == null) {
          String rootPath = ContainerUtil.getLastItem(names);
          fsRoot = ManagingFS.getInstance().findRoot(rootPath, fs instanceof ArchiveFileSystem ? LocalFileSystem.getInstance() : fs);
          if (fsRoot != null && !FileUtil.namesEqual(fsRoot.getName(), rootPath)) {
            // ignore really weird root names, like "/" under windows
            fsRoot = null;
          }
        }
        currentFile = fsRoot == null ? null : findFileFromRoot(fsRoot, currentFS, names, i);
      }
      else {
        currentFile = currentFile == null ? null : findChildThroughJar(currentFile, name, currentFS);
      }
      FilePartNode child = currentFile == null ? new UrlPartNode(name, myUrl(currentNode.myFileOrUrl), currentFS)
                                               : new FilePartNode(name.equals(JarFileSystem.JAR_SEPARATOR) ? JAR_SEPARATOR_NAME_ID : getNameId(currentFile), currentFile, currentFS);

      currentNode.children = ArrayUtil.insert(currentNode.children, -index - 1, child);
      parentNode = currentNode;
      currentNode = child;
    }
    return new NodeToUpdate(parentNode, currentNode);
  }

  @NotNull
  static List<String> splitNames(@NotNull String path) {
    List<String> names = new ArrayList<>(20);
    int end = path.length();
    if (end == 0) return names;
    while (true) {
      boolean isJarSeparator =
        StringUtil.endsWith(path, 0, end, JarFileSystem.JAR_SEPARATOR) && end > 2 && path.charAt(end - 3) != '/';
      if (isJarSeparator) {
        names.add(JarFileSystem.JAR_SEPARATOR);
        end = end - 2;
        continue;
      }
      if (path.charAt(end-1) == '/') {
        end--;
      }
      if (end == 0 && path.charAt(0) == '/') {
        end = 1; // here's this weird ROOT file in temp system
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
    // start from before-the-last because it's the root, which we already found
    for (int i = names.size() - 2; i >= startIndex; i--) {
      String name = names.get(i);
      file = findChildThroughJar(file, name, fs);
      if (file == null) break;
    }
    return file;
  }

  // returns start index of the name (i.e. path[return..length) is considered a name)
  private static int extractName(@NotNull CharSequence path, int length) {
    if (length == 1 && path.charAt(0) == '/') {
      return 0; // in case of TEMP file system there is this weird ROOT file
    }
    int i = StringUtil.lastIndexOf(path, '/', 0, length);
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
      VirtualFile file = myFile(node.myFileOrUrl);
      if (file == null) {
        List<String> parts = splitNames(VfsUtilCore.urlToPath(myUrl(node.myFileOrUrl)));
        removeEmptyNodesByPath(parts);
      }
      else {
        List<VirtualFile> parts = getHierarchy(file, (NewVirtualFileSystem)file.getFileSystem());
        removeEmptyNodesByFile(parts);
      }
    }
  }

  void checkConsistency() {
    if (VirtualFilePointerManagerImpl.IS_UNDER_UNIT_TEST && !ApplicationInfoImpl.isInStressTest()) {
      doCheckConsistency(null, "", myFS.getProtocol() + URLUtil.SCHEME_SEPARATOR);
    }
  }

  @NotNull
  static FilePartNodeRoot createFakeRoot(@NotNull NewVirtualFileSystem fs) {
    return new FilePartNodeRoot(fs);
  }
}
