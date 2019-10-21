/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.vfs.impl;

import com.intellij.openapi.application.impl.ApplicationInfoImpl;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.util.text.StringUtilRt;
import com.intellij.openapi.vfs.*;
import com.intellij.openapi.vfs.newvfs.ArchiveFileSystem;
import com.intellij.openapi.vfs.newvfs.ManagingFS;
import com.intellij.openapi.vfs.newvfs.NewVirtualFile;
import com.intellij.openapi.vfs.newvfs.NewVirtualFileSystem;
import com.intellij.openapi.vfs.newvfs.impl.FileNameCache;
import com.intellij.openapi.vfs.newvfs.impl.NullVirtualFile;
import com.intellij.openapi.vfs.newvfs.impl.VirtualFileSystemEntry;
import com.intellij.openapi.vfs.pointers.VirtualFilePointer;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.PathUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/**
 * Trie data structure for succinct storage and fast retrieval of file pointers.
 * File pointer "a/b/x.txt" is stored in the tree with nodes a->b->x.txt
 */
class FilePointerPartNode {
  private static final FilePointerPartNode[] EMPTY_ARRAY = new FilePointerPartNode[0];
  private final int nameId; // name id of the VirtualFile corresponding to this node
  @NotNull
  FilePointerPartNode[] children = EMPTY_ARRAY; // sorted by this.getName()
  final FilePointerPartNode parent;
  // file pointers for this exact path (e.g. concatenation of all "part" fields down from the root).
  // Either VirtualFilePointerImpl or VirtualFilePointerImpl[] (when it so happened that several pointers merged into one node - e.g. after file rename onto existing pointer)
  private Object leaves;

  // in case there is file pointer exists for this part, its info is saved here
  volatile Pair<VirtualFile, String> myFileAndUrl; // must not be both null
  volatile long myLastUpdated = -1; // contains latest result of ManagingFS.getInstance().getStructureModificationCount()
  volatile int useCount;

  int pointersUnder;   // number of alive pointers in this node plus all nodes beneath
  private static final VirtualFileManager ourFileManager = VirtualFileManager.getInstance();

  private FilePointerPartNode(int nameId, @NotNull FilePointerPartNode parent) {
    assert nameId > 0 : nameId + "; " + getClass();
    this.nameId = nameId;
    this.parent = parent;
  }

  boolean urlEndsWithName(@NotNull String urlAfter, VirtualFile fileAfter) {
    if (fileAfter != null) {
      return nameId == getNameId(fileAfter);
    }
    return StringUtil.endsWith(urlAfter, getName());
  }

  @NotNull
  static FilePointerPartNode createFakeRoot() {
    return new FilePointerPartNode(null) {
      @Override
      public String toString() {
        return "root -> "+children.length;
      }

      @NotNull
      @Override
      CharSequence getName() {
        return "";
      }
    };
  }

  // for creating fake root
  FilePointerPartNode(FilePointerPartNode parent) {
    nameId = -1;
    this.parent = parent;
  }

  @NotNull
  static CharSequence fromNameId(int nameId) {
    return FileNameCache.getVFileName(nameId);
  }

  @NotNull
  CharSequence getName() {
    return FileNameCache.getVFileName(nameId);
  }

  @Override
  public String toString() {
    return getName() + (children.length == 0 ? "" : " -> "+children.length);
  }

  /**
   * Tries to match the given path (parent, childNameId) with the trie structure of FilePointerPartNodes
   * <p>Recursive nodes (i.e. the nodes containing VFP with recursive==true) will be added to outDirs.
   * @param parentNameId is equal to {@code parent != null ? parent.getName() : null}
   */
  private FilePointerPartNode matchById(@Nullable VirtualFile parent,
                                        int parentNameId,
                                        int childNameId,
                                        @Nullable List<? super FilePointerPartNode> outDirs,
                                        boolean createIfNotFound,
                                        @NotNull VirtualFileSystem fs) {
    assert childNameId != -1 && (parent == null) == (parentNameId == -1);
    FilePointerPartNode leaf;
    if (parent == null) {
      leaf = this;
    }
    else {
      VirtualFile gParent = getParentThroughJars(parent, fs);
      int gParentNameId = getNameId(gParent);
      leaf = matchById(gParent, gParentNameId, parentNameId, outDirs, createIfNotFound, fs);
      if (leaf == null) return null;
    }

    leaf.addRecursiveDirectoryPtrTo(outDirs);
    return leaf.findChildByNameId(childNameId, createIfNotFound);
  }

  private static int getNameId(VirtualFile file) {
    return file == null ? -1 : ((VirtualFileSystemEntry)file).getNameId();
  }

  private FilePointerPartNode findByExistingNameId(@Nullable VirtualFile parent,
                                                   int childNameId,
                                                   @Nullable List<? super FilePointerPartNode> outDirs,
                                                   @NotNull VirtualFileSystem fs) {
    if (childNameId <= 0) throw new IllegalArgumentException("invalid argument childNameId: "+childNameId);
    FilePointerPartNode leaf;
    if (parent == null) {
      leaf = this;
    }
    else {
      int nameId = getNameId(parent);
      VirtualFile gParent = getParentThroughJars(parent, fs);
      int gParentNameId = getNameId(gParent);
      leaf = matchById(gParent, gParentNameId, nameId, outDirs, false, fs);
      if (leaf == null) return null;
    }

    leaf.addRecursiveDirectoryPtrTo(outDirs);
    return leaf.findChildByNameId(childNameId, false);
  }


  // returns start index of the name (i.e. path[return..length) is considered a name)
  private static int extractName(@NotNull CharSequence path, int length) {
    if (length == 1 && path.charAt(0) == '/') {
      return 0; // in case of TEMP file system there is this weird ROOT file
    }
    int i = StringUtil.lastIndexOf(path, '/', 0, length);
    return i + 1;
  }

  private FilePointerPartNode findChildByNameId(int nameId, boolean createIfNotFound) {
    if (nameId <= 0) throw new IllegalArgumentException("invalid argument nameId: "+nameId);
    for (FilePointerPartNode child : children) {
      if (child.nameEqualTo(nameId)) return child;
    }
    if (createIfNotFound) {
      CharSequence name = fromNameId(nameId);
      int index = binarySearchChildByName(name);
      FilePointerPartNode child;
      assert index < 0 : index + " : child= '" + (child = children[index]) + "'"
                         + "; child.nameEqualTo(nameId)=" + child.nameEqualTo(nameId)
                         + "; child.getClass()=" + child.getClass()
                         + "; child.nameId=" + child.nameId
                         + "; child.getName()='" + child.getName() + "'"
                         + "; nameId=" + nameId
                         + "; name='" + name + "'"
                         + "; compare(child) = " + StringUtil.compare(child.getName(), name, !SystemInfo.isFileSystemCaseSensitive) + ";"
                         + " UrlPart.nameEquals: " + FileUtil.PATH_CHAR_SEQUENCE_HASHING_STRATEGY.equals(child.getName(), fromNameId(nameId))
                         + "; name.equals(child.getName())=" + name.equals(child.getName())
        ;
      child = new FilePointerPartNode(nameId, this);
      children = ArrayUtil.insert(children, -index-1, child);
      return child;
    }
    return null;
  }

  boolean nameEqualTo(int nameId) {
    return this.nameId == nameId;
  }

  private int binarySearchChildByName(@NotNull CharSequence name) {
    return ObjectUtils.binarySearch(0, children.length, i -> {
      FilePointerPartNode child = children[i];
      CharSequence childName = child.getName();
      return StringUtil.compare(childName, name, !SystemInfo.isFileSystemCaseSensitive);
    });
  }

  private void addRecursiveDirectoryPtrTo(@Nullable List<? super FilePointerPartNode> dirs) {
    if(dirs != null && hasRecursiveDirectoryPointer() && ContainerUtil.getLastItem(dirs) != this) {
      dirs.add(this);
    }
  }

  /**
   * Appends to {@code out} all nodes under this node whose path (beginning from this node) starts with the given path
   * ({@code (parent != null ? parent.getPath() : "") + (separator ? "/" : "") + childName}) and all nodes under this node with recursive directory pointers whose
   * path is ancestor of the given path.
   */
  void addRelevantPointersFrom(@Nullable VirtualFile parent,
                               int childNameId,
                               @NotNull List<? super FilePointerPartNode> out,
                               boolean addSubdirectoryPointers,
                               @NotNull VirtualFileSystem fs) {
    if (childNameId <= 0) throw new IllegalArgumentException("invalid argument childNameId: "+childNameId);
    FilePointerPartNode node = findByExistingNameId(parent, childNameId, out, fs);
    if (node != null) {
      if (node.leaves != null) {
        out.add(node);
      }
      if (addSubdirectoryPointers) {
        // when "a/b" changed, treat all "a/b/*" virtual file pointers as changed because that's what happens on directory rename "a"->"newA": "a" deleted and "newA" created
        addAllPointersStrictlyUnder(node, out);
      }
    }
  }

  private boolean hasRecursiveDirectoryPointer() {
    if (leaves == null) return false;
    if (leaves instanceof VirtualFilePointer) {
      return ((VirtualFilePointer)leaves).isRecursive();
    }
    VirtualFilePointerImpl[] leaves = (VirtualFilePointerImpl[])this.leaves;
    for (VirtualFilePointerImpl leaf : leaves) {
      if (leaf.isRecursive()) return true;
    }
    return false;
  }

  private static void addAllPointersStrictlyUnder(@NotNull FilePointerPartNode node, @NotNull List<? super FilePointerPartNode> out) {
    for (FilePointerPartNode child : node.children) {
      if (child.leaves != null) {
        out.add(child);
      }
      addAllPointersStrictlyUnder(child, out);
    }
  }

  void checkConsistency() {
    if (VirtualFilePointerManagerImpl.IS_UNDER_UNIT_TEST && !ApplicationInfoImpl.isInStressTest()) {
      doCheckConsistency();
    }
  }

  private void doCheckConsistency() {
    String name = getName().toString();
    assert !"..".equals(name) && !".".equals(name) : "url must not contain '.' or '..' but got: " + this;
    int childSum = 0;
    for (int i = 0; i < children.length; i++) {
      FilePointerPartNode child = children[i];
      childSum += child.pointersUnder;
      child.doCheckConsistency();
      assert child.parent == this;
      if (i != 0) {
        assert !FileUtil.namesEqual(child.getName().toString(), children[i-1].getName().toString()) : "child["+i+"] = "+child+"; [-1] = "+children[i-1];
      }
    }
    childSum += leavesNumber();
    assert (useCount == 0) == (leaves == null) : useCount + " - " + (leaves instanceof VirtualFilePointerImpl ? leaves : Arrays.toString((VirtualFilePointerImpl[])leaves));
    assert pointersUnder == childSum : "expected: "+pointersUnder+"; actual: "+childSum;
    Pair<VirtualFile, String> fileAndUrl = myFileAndUrl;
    if (fileAndUrl != null && fileAndUrl.second != null) {
      String url = fileAndUrl.second;
      String path = VfsUtilCore.urlToPath(url);
      path = StringUtil.trimEnd(path, JarFileSystem.JAR_SEPARATOR);
      String nameFromPath = PathUtil.getFileName(path);
      if (!path.isEmpty() && nameFromPath.isEmpty() && SystemInfo.isUnix) {
        nameFromPath = "/";
      }
      assert StringUtilRt.equal(nameFromPath, name, SystemInfo.isFileSystemCaseSensitive) : "fileAndUrl: " + fileAndUrl + "; but this: " + this+"; nameFromPath: "+nameFromPath+"; name: "+name+"; parent: "+parent+"; path: "+path+"; url: "+url;
    }
    boolean hasFile = fileAndUrl != null && fileAndUrl.first != null;
    if (hasFile) {
      assert fileAndUrl.first.getName().equals(name) : "fileAndUrl: " + fileAndUrl + "; but this: " + this;
    }
  }

  // returns root node
  @NotNull
  FilePointerPartNode remove() {
    int pointersNumber = leavesNumber();
    assert leaves != null : toString();
    associate(null, null);
    useCount = 0;
    FilePointerPartNode node;
    for (node = this; node.parent != null; node = node.parent) {
      int pointersAfter = node.pointersUnder-=pointersNumber;
      if (pointersAfter == 0) {
        node.parent.children = ArrayUtil.remove(node.parent.children, node);
        node.myFileAndUrl = null;
      }
    }
    if ((node.pointersUnder-=pointersNumber) == 0) {
      node.children = EMPTY_ARRAY; // clear root node, especially in tests
    }
    return node;
  }

  @Nullable("null means this node's myFileAndUrl became invalid")
  // returns pair.second != null always
  Pair<VirtualFile, String> update() {
    final long lastUpdated = myLastUpdated;
    final Pair<VirtualFile, String> fileAndUrl = myFileAndUrl;
    if (fileAndUrl == null) return null;
    final long fsModCount = ManagingFS.getInstance().getStructureModificationCount();
    if (lastUpdated == fsModCount) return fileAndUrl;
    VirtualFile file = fileAndUrl.first;
    String url = fileAndUrl.second;
    boolean changed = false;

    if (url == null) {
      url = file.getUrl();
      if (!file.isValid()) file = null;
      changed = true;
    }
    boolean fileIsValid = file != null && file.isValid();
    if (file != null && !fileIsValid) {
      file = null;
      changed = true;
    }
    if (file == null) {
      file = ourFileManager.findFileByUrl(url);
      fileIsValid = file != null && file.isValid();
      if (file != null) {
        changed = true;
      }
    }
    if (file != null) {
      if (fileIsValid) {
        url = file.getUrl(); // refresh url, it can differ
        changed |= !url.equals(fileAndUrl.second);
      }
      else {
        file = null; // can't find, try next time
        changed = true;
      }
    }
    Pair<VirtualFile, String> result;
    if (changed) {
      myFileAndUrl = result = Pair.create(file, url);
    }
    else {
      result = fileAndUrl;
    }
    myLastUpdated = fsModCount; // must be the last
    return result;
  }

  void associate(Object leaves, Pair<VirtualFile, String> fileAndUrl) {
    this.leaves = leaves;
    myFileAndUrl = fileAndUrl;
    // assign myNode last because .update() reads that field outside lock
    if (leaves != null) {
      if (leaves instanceof VirtualFilePointerImpl) {
        ((VirtualFilePointerImpl)leaves).myNode = this;
      }
      else {
        for (VirtualFilePointerImpl pointer : (VirtualFilePointerImpl[])leaves) {
          pointer.myNode = this;
        }
      }
    }
    myLastUpdated = -1;
  }

  int incrementUsageCount(int delta) {
    return useCount+=delta;
  }

  int numberOfPointersUnder() {
    return pointersUnder;
  }

  VirtualFilePointerImpl getAnyPointer() {
    Object leaves = this.leaves;
    return leaves == null ? null : leaves instanceof VirtualFilePointerImpl ? (VirtualFilePointerImpl)leaves : ((VirtualFilePointerImpl[])leaves)[0];
  }

  @NotNull
  private String getUrl() {
    return parent == null ? getName().toString() : parent.getUrl() + "/"+getName();
  }

  private int leavesNumber() {
    Object leaves = this.leaves;
    return leaves == null ? 0 : leaves instanceof VirtualFilePointerImpl ? 1 : ((VirtualFilePointerImpl[])leaves).length;
  }

  void addAllPointersTo(@NotNull Collection<? super VirtualFilePointerImpl> outList) {
    Object leaves = this.leaves;
    if (leaves == null) {
      return;
    }
    if (leaves instanceof VirtualFilePointerImpl) {
      outList.add((VirtualFilePointerImpl)leaves);
    }
    else {
      ContainerUtil.addAll(outList, (VirtualFilePointerImpl[])leaves);
    }
  }

  @NotNull
  FilePointerPartNode findOrCreateNodeByFile(@NotNull VirtualFile file, @NotNull NewVirtualFileSystem fs) {
    int nameId = getNameId(file);
    VirtualFile parent = getParentThroughJars(file, fs);
    int parentNameId = getNameId(parent);
    return matchById(parent, parentNameId, nameId, null, true, fs);
  }

  // for "file://a/b/c.txt" return "a/b", for "jar://a/b/j.jar!/c.txt" return "/a/b/j.jar"
  private static VirtualFile getParentThroughJars(@NotNull VirtualFile file, @NotNull VirtualFileSystem fs) {
    VirtualFile parent = file.getParent();
    if (parent == null && fs instanceof ArchiveFileSystem) {
      VirtualFile local = ((ArchiveFileSystem)fs).getLocalByEntry(file);
      if (local != null) {
        parent = local.getParent();
      }
    }
    return parent;
  }

  @NotNull
  static FilePointerPartNode findOrCreateNodeByPath(@NotNull FilePointerPartNode rootNode, @NotNull String path, @NotNull NewVirtualFileSystem fs) {
    List<String> names = splitNames(path);
    NewVirtualFile fsRoot = null;

    VirtualFile NEVER_TRIED_TO_FIND = NullVirtualFile.INSTANCE;
    // we try to never call file.findChild() because it's expensive
    VirtualFile currentFile = NEVER_TRIED_TO_FIND;
    FilePointerPartNode currentNode = rootNode;
    for (int i = names.size() - 1; i >= 0; i--) {
      String name = names.get(i);
      int index = currentNode.binarySearchChildByName(name);
      if (index >= 0) {
        currentNode = currentNode.children[index];
        currentFile = currentFile == NEVER_TRIED_TO_FIND || currentFile == null ? currentFile : currentFile.findChild(name);
        continue;
      }
      // create and insert new node
      // first, have to check if the file root/names(end)/.../names[i] exists
      // if yes, create nameId-based FilePinterPartNode (for faster search and memory efficiency),
      // if not, create temp UrlPartNode which will be replaced with FPPN when the real file is created
      if (currentFile == NEVER_TRIED_TO_FIND) {
        if (fsRoot == null) {
          String rootPath = ContainerUtil.getLastItem(names);
          fsRoot = ManagingFS.getInstance().findRoot(rootPath, fs instanceof ArchiveFileSystem ? LocalFileSystem.getInstance() : fs);
          if (fsRoot != null && !fsRoot.getName().equals(rootPath)) {
            // ignore really weird root names, like "/" under windows
            fsRoot = null;
          }
        }
        currentFile = fsRoot == null ? null : findFileFromRoot(fsRoot, fs, names, i);
      }
      else {
        currentFile = currentFile == null ? null : currentFile.findChild(name);
      }
      FilePointerPartNode child = currentFile == null ? new UrlPartNode(name, currentNode)
                                                      : new FilePointerPartNode(getNameId(currentFile), currentNode);

      currentNode.children = ArrayUtil.insert(currentNode.children, -index - 1, child);
      currentNode = child;
      if (i != 0 && fs instanceof ArchiveFileSystem && currentFile != null && !currentFile.isDirectory()) {
        currentFile = ((ArchiveFileSystem)fs).getRootByLocal(currentFile);
      }
    }
    return currentNode;
  }

  @NotNull
  private static List<String> splitNames(@NotNull String path) {
    List<String> names = new ArrayList<>(20);
    int end = path.length();
    if (end == 0) return names;
    while (true) {
      int startIndex = extractName(path, end);
      assert startIndex != end : "startIndex: "+startIndex+"; end: "+end+"; path:'"+path+"'; toExtract: '"+path.substring(0, end)+"'";
      names.add(path.substring(startIndex, end));
      if (startIndex == 0) {
        break;
      }
      int skipSeparator = StringUtil.endsWith(path, 0, startIndex, JarFileSystem.JAR_SEPARATOR) && startIndex > 2 && path.charAt(startIndex-3) != '/' ? 2 : 1;
      end = startIndex - skipSeparator;
      if (end == 0 && path.charAt(0) == '/') {
        end = 1; // here's this weird ROOT file in temp system
      }
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
      file = file.findChild(name);
      if (fs instanceof ArchiveFileSystem && file != null && !file.isDirectory() && file.getFileSystem() != fs) {
        file = ((ArchiveFileSystem)fs).getRootByLocal(file);
      }
      if (file == null) break;
    }
    return file;
  }
}
