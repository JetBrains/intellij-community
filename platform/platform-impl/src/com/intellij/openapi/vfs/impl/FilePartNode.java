// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vfs.impl;

import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.util.text.StringUtilRt;
import com.intellij.openapi.vfs.*;
import com.intellij.openapi.vfs.newvfs.ArchiveFileSystem;
import com.intellij.openapi.vfs.newvfs.NewVirtualFileSystem;
import com.intellij.openapi.vfs.newvfs.impl.FileNameCache;
import com.intellij.openapi.vfs.newvfs.impl.VirtualFileSystemEntry;
import com.intellij.openapi.vfs.pointers.VirtualFilePointerListener;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.PathUtil;
import com.intellij.util.containers.MultiMap;
import com.intellij.util.io.URLUtil;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Trie data structure for succinct storage and fast retrieval of file pointers.
 * File pointer "a/b/x.txt" is stored in the tree with nodes a->b->x.txt
 */
class FilePartNode {
  private static final FilePartNode[] EMPTY_ARRAY = new FilePartNode[0];
  static final int JAR_SEPARATOR_NAME_ID = -2;
  private final int nameId; // name id of the VirtualFile corresponding to this node
  FilePartNode @NotNull [] children = EMPTY_ARRAY; // sorted by this.getName(). elements never updated inplace
  // file pointers for this exact path (i.e. concatenation of all getName() down from the root).
  // Either VirtualFilePointerImpl or VirtualFilePointerImpl[] (when it so happened that several pointers merged into one node - e.g. after file rename onto existing pointer)
  private Object leaves;
  @NotNull
  volatile Object myFileOrUrl;
  final NewVirtualFileSystem myFS; // the file system of this particular component. E.g. for path "/x.jar!/foo.txt" the node "x.jar" fs is LocalFileSystem, the node "foo.txt" fs is JarFileSystem

  FilePartNode(int nameId,
               @NotNull Object fileOrUrl,
               @NotNull NewVirtualFileSystem fs) {
    myFS = fs;
    assert nameId > 0 || nameId == JAR_SEPARATOR_NAME_ID : nameId + "; " + getClass();
    this.nameId = nameId;
    myFileOrUrl = fileOrUrl;
    if (fileOrUrl instanceof VirtualFile) {
      assert myFile().getFileSystem() == myFS : "myFs=" + myFS + "; myFile().getFileSystem()=" + myFile().getFileSystem() + "; " + fileOrUrl;
      if (myFile().getParent() == null && fs instanceof ArchiveFileSystem) {
        assert nameId == JAR_SEPARATOR_NAME_ID : nameId;
      }
    }
  }

  private VirtualFile myFile() {
    return myFile(myFileOrUrl);
  }

  void addLeaf(@NotNull VirtualFilePointerImpl pointer) {
    Object leaves = this.leaves;
    Object newLeaves;
    if (leaves == null) {
      newLeaves = pointer;
    }
    else if (leaves instanceof VirtualFilePointerImpl) {
      newLeaves = new VirtualFilePointerImpl[]{(VirtualFilePointerImpl)leaves, pointer};
    }
    else {
      newLeaves = ArrayUtil.append((VirtualFilePointerImpl[])leaves, pointer);
    }
    associate(newLeaves);
  }
  // return remaining leaves number
  int removeLeaf(@NotNull VirtualFilePointerImpl pointer) {
    Object leaves = this.leaves;
    if (leaves == null) {
      return 0;
    }
    if (leaves instanceof VirtualFilePointerImpl) {
      if (leaves == pointer) {
        this.leaves = null;
        return 0;
      }
      return 1;
    }
    VirtualFilePointerImpl[] newLeaves = ArrayUtil.remove((VirtualFilePointerImpl[])leaves, pointer);
    if (newLeaves.length == 0) newLeaves = null;
    this.leaves = newLeaves;
    return newLeaves == null ? 0 : newLeaves.length;
  }

  static VirtualFile myFile(@NotNull Object fileOrUrl) {
    return fileOrUrl instanceof VirtualFile ? (VirtualFile)fileOrUrl : null;
  }

  @NotNull
  private String myUrl() {
    return myUrl(myFileOrUrl);
  }

  @NotNull
  static String myUrl(Object fileOrUrl) {
    return fileOrUrl instanceof VirtualFile ? ((VirtualFile)fileOrUrl).getUrl() : (String)fileOrUrl;
  }

  // for creating fake root
  FilePartNode(@NotNull NewVirtualFileSystem fs) {
    nameId = -1;
    myFileOrUrl = "";
    myFS = fs;
  }

  @NotNull
  static CharSequence fromNameId(int nameId) {
    return nameId == JAR_SEPARATOR_NAME_ID ? JarFileSystem.JAR_SEPARATOR : FileNameCache.getVFileName(nameId);
  }

  @NotNull
  CharSequence getName() {
    return fromNameId(nameId);
  }

  @Override
  public String toString() {
    return getName() + (children.length == 0 ? "" : " -> "+children.length);
  }

  static int getNameId(@NotNull VirtualFile file) {
    VirtualFileSystem fs = file.getFileSystem();
    if (fs instanceof ArchiveFileSystem && file.getParent() == null) {
      return JAR_SEPARATOR_NAME_ID;
    }
    
    return ((VirtualFileSystemEntry)file).getNameId();
  }

  @Contract("_, _, true, _ -> !null")
  FilePartNode findChildByNameId(@Nullable VirtualFile file,
                                 int nameId,
                                 boolean createIfNotFound,
                                 @NotNull NewVirtualFileSystem childFs) {
    if (nameId <= 0 && nameId != JAR_SEPARATOR_NAME_ID) throw new IllegalArgumentException("invalid argument nameId: "+nameId);
    for (FilePartNode child : children) {
      if (child.nameEqualTo(nameId)) return child;
    }
    if (createIfNotFound) {
      CharSequence name = fromNameId(nameId);
      int index = children.length == 0 ? -1 : binarySearchChildByName(name);
      FilePartNode child;
      assert index < 0 : index + " : child= '" + (child = children[index]) + "'"
                         + "; child.nameEqualTo(nameId)=" + child.nameEqualTo(nameId)
                         + "; child.getClass()=" + child.getClass()
                         + "; child.nameId=" + child.nameId
                         + "; child.getName()='" + child.getName() + "'"
                         + "; nameId=" + nameId
                         + "; name='" + name + "'"
                         + "; compare(child) = " + StringUtil.compare(child.getName(), name, !SystemInfo.isFileSystemCaseSensitive) + ";"
                         + " UrlPart.nameEquals: " + FileUtil.PATH_CHAR_SEQUENCE_HASHING_STRATEGY.equals(child.getName(), fromNameId(nameId))
                         + "; name.equals(child.getName())=" + child.getName().equals(name)
        ;
      Object fileOrUrl = file;
      if (fileOrUrl == null) {
        fileOrUrl = this.nameId == -1 ? name.toString() : childUrl(myUrl(), name, childFs);
      }
      child = new FilePartNode(nameId, fileOrUrl, childFs);
      children = ArrayUtil.insert(children, -index-1, child);
      return child;
    }
    return null;
  }

  boolean nameEqualTo(int nameId) {
    return this.nameId == nameId;
  }

  int binarySearchChildByName(@NotNull CharSequence name) {
    return ObjectUtils.binarySearch(0, children.length, i -> {
      FilePartNode child = children[i];
      CharSequence childName = child.getName();
      return StringUtil.compare(childName, name, !SystemInfo.isFileSystemCaseSensitive);
    });
  }

  void addRecursiveDirectoryPtrTo(@NotNull MultiMap<? super VirtualFilePointerListener, ? super VirtualFilePointerImpl> toFirePointers) {
    processPointers(pointer -> { if (pointer.isRecursive()) toFirePointers.putValue(pointer.myListener, pointer); });
  }

  void doCheckConsistency(@Nullable VirtualFile parent, @NotNull String name, @NotNull String urlFromRoot) {
    VirtualFile myFile = myFile();

    if (!(this instanceof FilePartNodeRoot)) {
      if (myFile == null) {
        String myUrl = myUrl();
        String expectedUrl = StringUtil.trimEnd(urlFromRoot, '/');
        String actualUrl = StringUtil.trimEnd(myUrl, '/');
        assert FileUtil.namesEqual(actualUrl, expectedUrl) : "Expected url: '" + expectedUrl + "' but got: '" + actualUrl + "'";
      }
      else {
        assert Comparing.equal(getParentThroughJar(myFile, myFS), parent) : "parent: " + parent + "; myFile: " + myFile;
      }
    }
    assert !"..".equals(name) && !".".equals(name) : "url must not contain '.' or '..' but got: " + this;
    String prevChildName = "";
    for (int i = 0; i < children.length; i++) {
      FilePartNode child = children[i];
      String childName = child.getName().toString();
      boolean needSeparator = !urlFromRoot.isEmpty() && !urlFromRoot.endsWith("/") && !childName.equals(JarFileSystem.JAR_SEPARATOR);
      String childUrlFromRoot = needSeparator ? urlFromRoot + "/" + childName : urlFromRoot + childName;
      if (child.myFS != myFS) {
        // "file:" changed to "jar:"
        childUrlFromRoot = child.myFS.getProtocol() + StringUtil.trimStart(childUrlFromRoot, myFS.getProtocol());
      }
      child.doCheckConsistency(myFile, childName, childUrlFromRoot);
      if (i != 0) {
        assert !FileUtil.namesEqual(childName, prevChildName) : "child[" + i + "] = " + child + "; [-1] = " + children[i - 1];
      }
      prevChildName = childName;
    }
    int[] leafNumber = new int[1];
    processPointers(p -> { assert p.myNode == this; leafNumber[0]++; });
    int useCount = leafNumber[0];
    assert (useCount == 0) == (leaves == null) : useCount + " - " + (leaves instanceof VirtualFilePointerImpl ? leaves : Arrays.toString((VirtualFilePointerImpl[])leaves));

    if (myFileOrUrl instanceof String) {
      String myPath = VfsUtilCore.urlToPath(myUrl());
      String nameFromPath = nameId == JAR_SEPARATOR_NAME_ID || myPath.endsWith(JarFileSystem.JAR_SEPARATOR) ? JarFileSystem.JAR_SEPARATOR : PathUtil.getFileName(myPath);
      if (!myPath.isEmpty() && nameFromPath.isEmpty()) {
        nameFromPath = "/";
      }
      assert StringUtilRt.equal(nameFromPath, name, SystemInfo.isFileSystemCaseSensitive) : "fileAndUrl: " + myFileOrUrl + "; but this: " + this + "; nameFromPath: " + nameFromPath + "; name: " + name + "; myPath: " + myPath + "; url: " + myUrl() + ";";
      if (myFile != null) {
        String fileName = myFile.getParent() == null && myFile.getFileSystem() instanceof ArchiveFileSystem ? JarFileSystem.JAR_SEPARATOR : myFile.getName();
        assert fileName.equals(name) : "fileAndUrl: " + myFileOrUrl + "; but this: " + this;
        assert myFile.getFileSystem() == myFS;
      }
    }
  }

  // update myFileOrUrl to a VirtualFile and replace UrlPartNode with FilePartNode if the file exists, including all subnodes
  void update(@NotNull FilePartNode parent, @NotNull FilePartNodeRoot root) {
    Object fileOrUrl = myFileOrUrl;
    VirtualFile file = myFile(fileOrUrl);
    boolean changed = false;
    boolean nameChanged = false;
    boolean fileIsValid = false;
    if (file != null) {
      fileIsValid = file.isValid();
      if (fileIsValid && file.getParent() == null && file.getFileSystem() instanceof ArchiveFileSystem) {
        VirtualFile local = ((ArchiveFileSystem)file.getFileSystem()).getLocalByEntry(file);
        fileIsValid = local != null;
      }
      if (!fileIsValid) {
        file = null;
        changed = true;
      }
    }

    Object parentFileOrUrl;
    parentFileOrUrl = parent.myFileOrUrl;
    String myName = getName().toString();
    String url = null;
    String parentUrl = null;

    VirtualFile parentFile = myFile(parentFileOrUrl);
    if (file == null) {
      file = parentFile == null || !parentFile.isValid() ? null : findChildThroughJar(parentFile, myName, myFS);
      if (file == null) {
        parentUrl = myUrl(parentFileOrUrl);
        url = childUrl(parentUrl, myName, myFS);
        changed |= nameChanged = !Comparing.strEqual(url, myUrl(fileOrUrl));
      }
      else {
        changed = true;
      }
      fileIsValid = file != null && file.isValid();
    }
    if (parent.nameId != -1 && !(parentFileOrUrl instanceof VirtualFile) && file != null) {
      // if parent file can't be found then the child is not valid too
      file = null;
      fileIsValid = false;
      url = myUrl(fileOrUrl);
    }
    if (file != null) {
      if (fileIsValid) {
        changed |= nameChanged = !StringUtil.equals(file.getNameSequence(), myName);
      }
      else {
        file = null; // can't find, try next time
        changed = true;
        url = myUrl(fileOrUrl);
      }
    }
    Object result = file == null ? url : file;
    changed |= !Objects.equals(fileOrUrl, result);
    FilePartNode thisNode = this;
    if (changed) {
      myFileOrUrl = result;
      if (file != null && (this instanceof UrlPartNode || nameChanged)) {
        // replace with FPPN if the actual file's appeared on disk to save memory with nameIds
        thisNode = replaceWithFPPN(file, parent);
      }
    }
    if (file != null && !Objects.equals(getParentThroughJar(file, myFS), parentFile)) {
      // this node file must be moved to the other dir. remove and re-insert from the root to the correct path, preserving all children
      FilePartNode newNode = root.findOrCreateByFile(file).node;
      processPointers(p-> newNode.addLeaf(p));
      newNode.children = children;
      children = EMPTY_ARRAY;
      changed = true;
      String myOldPath = VfsUtilCore.urlToPath(childUrl(parentUrl=myUrl(parentFileOrUrl), myName, myFS));
      root.removeEmptyNodesByPath(FilePartNodeRoot.splitNames(myOldPath));
      thisNode = newNode;
      nameChanged = true;
    }
    if (nameChanged) {
      String myOldPath = VfsUtilCore.urlToPath(childUrl(parentUrl == null ? myUrl(parentFileOrUrl) : parentUrl, myName, myFS));
      String myNewPath = VfsUtilCore.urlToPath(myUrl(result));
      // fix UrlPartNodes with (now) wrong url start
      thisNode.fixUrlPartNodes(myOldPath, myNewPath);
    }

    if (changed) {
      for (FilePartNode child : thisNode.children) {
        child.update(thisNode, root);
      }
    }
  }

  private void fixUrlPartNodes(@NotNull String oldPath, @NotNull String newPath) {
    if (this instanceof UrlPartNode) {
      String protocol = myFS.getProtocol();
      String myUrl = myUrl();
      if (StringUtil.startsWith(myUrl, protocol.length()+URLUtil.SCHEME_SEPARATOR.length(), oldPath)) {
        myFileOrUrl = protocol + URLUtil.SCHEME_SEPARATOR + newPath + myUrl.substring(protocol.length() + URLUtil.SCHEME_SEPARATOR.length()+oldPath.length());
      }
    }
    for (FilePartNode child : children) {
      child.fixUrlPartNodes(oldPath, newPath);
    }
  }

  @NotNull
  private FilePartNode replaceWithFPPN(@NotNull VirtualFile file, @NotNull FilePartNode parent) {
    int nameId = getNameId(file);
    parent.children = ArrayUtil.remove(parent.children, this);
    FilePartNode newNode = parent.findChildByNameId(file, nameId, true, (NewVirtualFileSystem)file.getFileSystem());
    newNode.children = children; // old children are destroyed when renamed onto their parent
    processPointers(pointer-> newNode.addLeaf(pointer));

    leaves = null;

    return newNode;
  }

  @NotNull
  static String childUrl(@NotNull String parentUrl, @NotNull CharSequence childName, @NotNull NewVirtualFileSystem fs) {
    if (childName.equals(JarFileSystem.JAR_SEPARATOR) && fs instanceof ArchiveFileSystem) {
      return VirtualFileManager.constructUrl(fs.getProtocol(), StringUtil.trimEnd(VfsUtilCore.urlToPath(parentUrl), '/')) + childName;
    }
    return parentUrl.isEmpty() ? VirtualFileManager.constructUrl(fs.getProtocol(), childName.toString()) :
           VirtualFileManager.constructUrl(fs.getProtocol(), StringUtil.trimEnd(VfsUtilCore.urlToPath(parentUrl), '/')) + '/' + childName;
  }

  private void associate(@Nullable Object leaves) {
    this.leaves = leaves;
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
  }

  VirtualFilePointerImpl getPointer(VirtualFilePointerListener listener) {
    Object leaves = this.leaves;
    if (leaves == null) {
      return null;
    }
    if (leaves instanceof VirtualFilePointerImpl) {
      VirtualFilePointerImpl leaf = (VirtualFilePointerImpl)leaves;
      return leaf.myListener == listener ? leaf : null;
    }
    VirtualFilePointerImpl[] array = (VirtualFilePointerImpl[])leaves;
    for (VirtualFilePointerImpl pointer : array) {
      if (pointer.myListener == listener) return pointer;
    }
    return null;
  }

  void addAllPointersTo(@NotNull Collection<? super VirtualFilePointerImpl> outList) {
    processPointers(p->{ if (p.myNode != null) outList.add(p); });
  }

  void processPointers(@NotNull Consumer<? super VirtualFilePointerImpl> processor) {
    Object leaves = this.leaves;
    if (leaves == null) {
      return;
    }
    if (leaves instanceof VirtualFilePointerImpl) {
      processor.accept((VirtualFilePointerImpl)leaves);
      return;
    }
    VirtualFilePointerImpl[] pointers = (VirtualFilePointerImpl[])leaves;
    for (VirtualFilePointerImpl pointer : pointers) {
      processor.accept(pointer);
    }
  }

  // for "file://a/b/c.txt" return "a/b", for "jar://a/b/j.jar!" return "file://a/b/j.jar"
  static VirtualFile getParentThroughJar(@NotNull VirtualFile file, @NotNull NewVirtualFileSystem fs) {
    VirtualFile parent = file.getParent();
    if (parent == null && fs instanceof ArchiveFileSystem) {
      parent = ((ArchiveFileSystem)fs).getLocalByEntry(file);
    }
    return parent;
  }

  static VirtualFile findChildThroughJar(@NotNull VirtualFile file, @NotNull String name, @NotNull NewVirtualFileSystem childFs) {
    VirtualFile child;
    if (name.equals(JarFileSystem.JAR_SEPARATOR) && childFs instanceof ArchiveFileSystem) {
      child = ((ArchiveFileSystem)childFs).getRootByLocal(file);
    }
    else {
      child = file.findChild(name);
    }
    return child;
  }

  boolean removeEmptyNodesByFile(@NotNull List<VirtualFile> parts) {
    if (parts.isEmpty()) {
      return children.length == 0;
    }
    VirtualFile file = parts.remove(parts.size()-1);
    FilePartNode child = findChildByNameId(null, getNameId(file), false, (NewVirtualFileSystem)file.getFileSystem());
    if (child == null) {
      return false;
    }
    boolean toRemove = child.removeEmptyNodesByFile(parts);
    if (toRemove) {
      children = children.length == 1 ? EMPTY_ARRAY : ArrayUtil.remove(children, child);
      return children.length == 0 && leaves == null;
    }
    return false;
  }

  boolean removeEmptyNodesByPath(@NotNull List<String> parts) {
    if (parts.isEmpty()) {
      return children.length == 0;
    }
    String name = parts.remove(parts.size()-1);
    int index = binarySearchChildByName(name);
    if (index < 0) {
      return false;
    }
    FilePartNode child = children[index];
    boolean toRemove = child.removeEmptyNodesByPath(parts);
    if (toRemove) {
      children = children.length == 1 ? EMPTY_ARRAY : ArrayUtil.remove(children, child);
      return children.length == 0 && leaves == null;
    }
    return false;
  }
}
