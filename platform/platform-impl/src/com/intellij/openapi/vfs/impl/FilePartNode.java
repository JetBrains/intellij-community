// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.impl;

import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.SystemInfoRt;
import com.intellij.openapi.util.io.FileSystemUtil;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.util.text.StringUtilRt;
import com.intellij.openapi.vfs.*;
import com.intellij.openapi.vfs.newvfs.ArchiveFileSystem;
import com.intellij.openapi.vfs.newvfs.NewVirtualFileSystem;
import com.intellij.openapi.vfs.newvfs.VfsImplUtil;
import com.intellij.openapi.vfs.newvfs.impl.VirtualFileSystemEntry;
import com.intellij.openapi.vfs.newvfs.persistent.FSRecords;
import com.intellij.openapi.vfs.pointers.VirtualFilePointerListener;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.PathUtil;
import com.intellij.util.containers.CollectionFactory;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import com.intellij.util.io.URLUtil;
import org.jetbrains.annotations.*;

import java.io.File;
import java.util.*;
import java.util.function.Consumer;

/**
 * Trie data structure for succinct storage and fast retrieval of file pointers.
 * File pointer "a/b/x.txt" is stored in the tree with nodes a->b->x.txt
 */
@ApiStatus.Internal
public class FilePartNode {
  public static final FilePartNode[] EMPTY_ARRAY = new FilePartNode[0];

  protected static final int JAR_SEPARATOR_NAME_ID = -2;

  private final int nameId; // name id of the VirtualFile corresponding to this node
  /** Sorted by this.getName(), with case-sensitivity from {@link #fs}. Elements are never updated inplace */
  FilePartNode @NotNull [] children = EMPTY_ARRAY;
  /**
   * file pointers for this exact path (i.e., concatenation of all getName() down from the root).
   * Either VirtualFilePointerImpl or VirtualFilePointerImpl[] (when it so happened that several pointers merged into one node - e.g., after file rename onto existing pointer)
   */
  private Object leaves;
  /** VirtualFile | String (=file url) */
  volatile @NotNull Object fileOrUrl;

  /** The file system of this particular component. E.g. for path "/x.jar!/foo.txt" the node "x.jar" fs is LocalFileSystem, the node "foo.txt" fs is JarFileSystem */
  @VisibleForTesting
  public final NewVirtualFileSystem fs;

  FilePartNode(int nameId,
               @NotNull Object fileOrUrl,
               @NotNull NewVirtualFileSystem fs) {
    assert nameId > 0 || nameId == JAR_SEPARATOR_NAME_ID : nameId + "; " + getClass();
    this.nameId = nameId;
    this.fs = fs;

    this.fileOrUrl = fileOrUrl;
    if (fileOrUrl instanceof VirtualFile file) {
      assert file.getFileSystem() == this.fs : "myFs=" + this.fs + "; myFile().getFileSystem()=" + file.getFileSystem() + "; " + fileOrUrl;
      if (file.getParent() == null && fs instanceof ArchiveFileSystem) {
        assert nameId == JAR_SEPARATOR_NAME_ID : nameId;
      }
    }
  }

  // for subclasses
  protected FilePartNode(@NotNull NewVirtualFileSystem fs) {
    nameId = -1;
    fileOrUrl = "";
    this.fs = fs;
  }

  @TestOnly
  public @NotNull Object getFileOrUrl() {
    return fileOrUrl;
  }

  private VirtualFile fileOrNull() {
    return fileOrNull(fileOrUrl);
  }

  private @NotNull String url() {
    return urlOf(fileOrUrl);
  }

  boolean isUrlBased() {
    return fileOrUrl instanceof String;
  }

  /** @return VirtualFile if fileOrUrl is a file, or null, if fileOrUrl is string URL */
  static @Nullable VirtualFile fileOrNull(@NotNull Object fileOrUrl) {
    return fileOrUrl instanceof VirtualFile ? (VirtualFile)fileOrUrl : null;
  }

  /** @return URL from fileOrUrl  */
  static @NotNull String urlOf(@NotNull Object fileOrUrl) {
    return fileOrUrl instanceof VirtualFile ? ((VirtualFile)fileOrUrl).getUrl() : (String)fileOrUrl;
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

  /** return remaining leaves number */
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

  static @NotNull CharSequence fromNameId(int nameId) {
    return nameId == JAR_SEPARATOR_NAME_ID ? JarFileSystem.JAR_SEPARATOR : FSRecords.getInstance().getNameByNameId(nameId);
  }

  @NotNull CharSequence getName() {
    return fromNameId(nameId);
  }

  @Override
  public String toString() {
    return "FilePartNode: '" + getName() + "'; children: " + children.length + "; fs=" +
           fs + "; myFileOrUrl=" + fileOrUrl + "; " + fileOrUrl.getClass();
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
      if (child.nameEqualTo(nameId, childFs)) return child;
    }
    if (createIfNotFound) {
      CharSequence name = fromNameId(nameId);
      int index = children.length == 0 ? -1 : binarySearchChildByName(name);
      FilePartNode child;
      //@formatter:off
      if (index >= 0) throw new AssertionError(index + " : child= '" + (child = children[index]) + "'"
                         + "; child.nameEqualTo(nameId)=" + child.nameEqualTo(nameId, childFs)
                         + "; child.getClass()=" + child.getClass()
                         + "; child.nameId=" + child.nameId
                         + "; child.getName()='" + child.getName() + "'"
                         + "; nameId=" + nameId
                         + "; name='" + name + "'"
                         + "; compare(child) = " + StringUtil.compare(child.getName(), name, !isCaseSensitive()) + ";"
                         + " UrlPart.nameEquals: " + StringUtilRt.equal(child.getName(), fromNameId(nameId), SystemInfoRt.isFileSystemCaseSensitive)
                         + "; name.equals(child.getName())=" + child.getName().equals(name)
                         + "; file=" + file
                         + "; this.isCaseSensitive()=" + isCaseSensitive());
      //@formatter:on
      Object fileOrUrl = file;
      if (fileOrUrl == null) {
        fileOrUrl = this.nameId == -1 ? name.toString() : childUrl(url(), name, childFs);
      }
      child = new FilePartNode(nameId, fileOrUrl, childFs);
      children = ArrayUtil.insert(children, -index-1, child);
      return child;
    }
    return null;
  }

  /** @return true if this part's name is equal to the name denoted by nameId, with case-sensitivity from childFs */
  boolean nameEqualTo(int nameId,
                      @NotNull NewVirtualFileSystem childFs) {
    if (this.nameId == nameId) {
      return true;
    }
    boolean caseSensitive = childFs.isCaseSensitive();
    if (caseSensitive) {
      return false;
    }

    return StringUtilRt.equal(getName(), fromNameId(nameId), caseSensitive);
  }

  int binarySearchChildByName(@NotNull CharSequence name) {
    boolean ignoreCase = !isCaseSensitive();
    return ObjectUtils.binarySearch(0, children.length, i -> {
      FilePartNode child = children[i];
      CharSequence childName = child.getName();
      return StringUtil.compare(childName, name, ignoreCase);
    });
  }

  void addRecursiveDirectoryPtrTo(@NotNull MultiMap<? super VirtualFilePointerListener, ? super VirtualFilePointerImpl> toFirePointers) {
    processPointers(pointer -> { if (pointer.isRecursive()) toFirePointers.putValue(pointer.myListener, pointer); });
  }

  void doCheckConsistency(@Nullable VirtualFile parent, @NotNull String name, @NotNull String urlFromRoot) {
    VirtualFile myFile = fileOrNull();

    if (!(this instanceof FilePartNodeRoot)) {
      if (myFile == null) {
        String myUrl = url();
        String expectedUrl = StringUtil.trimEnd(urlFromRoot, '/');
        String actualUrl = StringUtil.trimEnd(myUrl, '/');
        assert FileUtil.namesEqual(actualUrl, expectedUrl) : "Expected url: '" + expectedUrl + "' but got: '" + actualUrl + "'; parent="+parent+"; name="+name+"; urlFromParent="+urlFromRoot;
      }
      else {
        assert Comparing.equal(getParentThroughJar(myFile, fs), parent) :
          "parent: " + parent + "\n myFile: " + myFile + "\n getParentThroughJar(myFile, myFS): " + getParentThroughJar(myFile, fs) +
          "\n myFS: " + fs + "\n myFile.getParent(): " + myFile.getParent() + "\n this: " + this;
      }
    }
    assert !"..".equals(name) && !".".equals(name) : "url must not contain '.' or '..' but got: " + this;
    String prevChildName = "";
    Set<String> childNames = CollectionFactory.createFilePathSet(children.length, isCaseSensitive());
    for (int i = 0; i < children.length; i++) {
      FilePartNode child = children[i];
      String childName = child.getName().toString();
      boolean added = childNames.add(childName);
      assert added : "'" + childName + "' is already in the childNames set (" + childNames + "). isCaseSensitive()=" + isCaseSensitive() + ";\n all children=" + Arrays.toString(children);

      boolean needSeparator = !urlFromRoot.isEmpty() && !urlFromRoot.endsWith("/") && !childName.equals(JarFileSystem.JAR_SEPARATOR);
      String childUrlFromRoot = needSeparator ? urlFromRoot + "/" + childName : urlFromRoot + childName;
      if (child.fs != fs) {
        // "file:" changed to "jar:"
        childUrlFromRoot = child.fs.getProtocol() + StringUtil.trimStart(childUrlFromRoot, fs.getProtocol());
      }
      child.doCheckConsistency(myFile, childName, childUrlFromRoot);
      if (i != 0) {
        assert StringUtil.compare(prevChildName, childName, !isCaseSensitive()) < 0: "children[" + i + "] = " + child + "; [-1] = " + children[i - 1] +"; isCaseSensitive()="+isCaseSensitive();
      }
      // fs is allowed to change in one direction only: local->jar
      assert fs instanceof LocalFileSystem && (child.fs instanceof ArchiveFileSystem || child.fs instanceof LocalFileSystem)
             || fs instanceof ArchiveFileSystem && child.fs instanceof ArchiveFileSystem
        : "this: " + this + "; fs=" + fs + "; child[" + i + "] = " + child + "; fs=" + child.fs;
      // child of UrlPartNode can be only UrlPartNode
      assert !(this instanceof UrlPartNode) || child instanceof UrlPartNode : "this: " + this + "; fs=" +
                                                                              fs + "; child[" + i + "] = " + child + "; fs=" + child.fs;
      prevChildName = childName;
    }
    int[] leafNumber = new int[1];
    processPointers(p -> { assert p.myNode == this; leafNumber[0]++; });
    int useCount = leafNumber[0];
    assert (useCount == 0) == (leaves == null) : useCount + " - " + (leaves instanceof VirtualFilePointerImpl ? leaves : Arrays.toString((VirtualFilePointerImpl[])leaves));

    if (fileOrUrl instanceof String) {
      String myPath = VfsUtilCore.urlToPath(url());
      String nameFromPath = nameId == JAR_SEPARATOR_NAME_ID || myPath.endsWith(JarFileSystem.JAR_SEPARATOR) ? JarFileSystem.JAR_SEPARATOR : PathUtil.getFileName(myPath);
      if (!myPath.isEmpty() && nameFromPath.isEmpty()) {
        nameFromPath = "/";
      }
      assert StringUtilRt.equal(nameFromPath, name, isCaseSensitive()) : "fileAndUrl: " +
                                                                         fileOrUrl + "; but this: " + this + "; nameFromPath: " + nameFromPath + "; name: " + name + "; myPath: " + myPath + "; url: " + url() + ";";
      if (myFile != null) {
        String fileName = myFile.getParent() == null && myFile.getFileSystem() instanceof ArchiveFileSystem ? JarFileSystem.JAR_SEPARATOR : myFile.getName();
        assert fileName.equals(name) : "fileAndUrl: " + fileOrUrl + "; but this: " + this;
        assert myFile.getFileSystem() == fs;
      }
    }
  }

  // update myFileOrUrl to a VirtualFile and replace UrlPartNode with FilePartNode if the file exists, including all sub-nodes
  @VisibleForTesting
  public void update(@NotNull FilePartNode parent,
              @NotNull FilePartNodeRoot root,
              @NotNull String debugSource,
              @Nullable Object debugInvalidationReason) {
    boolean oldCaseSensitive = isCaseSensitive();
    Object fileOrUrl = this.fileOrUrl;
    VirtualFile file = fileOrNull(fileOrUrl);
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
    parentFileOrUrl = parent.fileOrUrl;
    String myName = getName().toString();
    String url = null;
    String parentUrl = null;

    VirtualFile parentFile = fileOrNull(parentFileOrUrl);
    if (file == null) {
      file = parentFile == null || !parentFile.isValid() ? null : findChildThroughJar(parentFile, myName, fs);
      if (file == null) {
        parentUrl = urlOf(parentFileOrUrl);
        url = childUrl(parentUrl, myName, fs);
        changed |= nameChanged = !Comparing.strEqual(url, urlOf(fileOrUrl));
      }
      else {
        changed = true;
      }
      fileIsValid = file != null && file.isValid();
    }
    if (parent.nameId != -1 && !(parentFileOrUrl instanceof VirtualFile) && file != null) {
      // if the parent file can't be found, then the child is not valid either
      file = null;
      fileIsValid = false;
      url = urlOf(fileOrUrl);
    }
    if (file != null) {
      if (fileIsValid) {
        changed |= nameChanged = !StringUtil.equals(file.getNameSequence(), myName);
      }
      else {
        file = null; // can't find, try next time
        changed = true;
        url = urlOf(fileOrUrl);
      }
    }
    Object result = file == null ? url : file;
    changed |= !Objects.equals(fileOrUrl, result);
    FilePartNode thisNode = this;
    if (changed) {
      VirtualFile oldFile = fileOrNull(fileOrUrl);
      if (oldFile != null && file == null && debugInvalidationReason != null) {
        ((VirtualFileSystemEntry)oldFile).appendInvalidationReason(debugSource, debugInvalidationReason);
      }
      this.fileOrUrl = result;
      if (file != null && (this instanceof UrlPartNode || nameChanged)) {
        // replace with FPPN if the actual file's appeared on disk to save memory with nameIds
        thisNode = replaceWithFPPN(file, parent);
      }
    }
    if (file != null && !Objects.equals(getParentThroughJar(file, fs), parentFile)) {
      // this node file must be moved to the other dir. remove and re-insert from the root to the correct path, preserving all children
      FilePartNode newNode = root.findOrCreateByFile(file).node;
      processPointers(p -> newNode.addLeaf(p));
      newNode.children = children;
      children = EMPTY_ARRAY;
      changed = true;
      root.removeEmptyNodesByPath(VfsUtilCore.urlToPath(childUrl(parentUrl = urlOf(parentFileOrUrl), myName, fs)));
      thisNode = newNode;
      nameChanged = true;
    }
    if (nameChanged) {
      String myOldPath = VfsUtilCore.urlToPath(childUrl(parentUrl == null ? urlOf(parentFileOrUrl) : parentUrl, myName, fs));
      String myNewPath = VfsUtilCore.urlToPath(urlOf(result));
      // fix UrlPartNodes with (now) wrong url start
      thisNode.fixUrlPartNodes(myOldPath, myNewPath);
    }

    FilePartNode[] children = thisNode.children;
    VirtualFile toReplaceParent = null;
    for (int i = 0; i < children.length; i++) {
      FilePartNode child = children[i];
      if (changed) {
        child.update(thisNode, root, debugSource, debugInvalidationReason);
        if (i >= thisNode.children.length) {
          break;
        }
        child = thisNode.children[i];
      }
      if (file == null) {
        VirtualFile childFile = child.fileOrNull();
        if (childFile != null) {
          // child found which has a file but this node doesn't, should replace me with FPPN
          toReplaceParent = getParentThroughJar(childFile, child.fs);
        }
      }
    }
    if (toReplaceParent != null) {
      thisNode = replaceWithFPPN(toReplaceParent, parent);
    }
    // sometimes the case sensitivity of the directory is established only after its children.
    // when it's changed, we should re-sort children according to the new case sensitivity
    VirtualFile newFile = thisNode.fileOrNull();
    boolean newCaseSensitive = thisNode.isCaseSensitive();
    if (newFile != null && newCaseSensitive != oldCaseSensitive) {
      Arrays.sort(thisNode.children, (c1, c2) -> StringUtil.compare(c1.getName(), c2.getName(), !newCaseSensitive));
    }
  }

  private void fixUrlPartNodes(@NotNull String oldPath, @NotNull String newPath) {
    if (this instanceof UrlPartNode) {
      String protocol = fs.getProtocol();
      String myUrl = url();
      if (StringUtil.startsWith(myUrl, protocol.length()+URLUtil.SCHEME_SEPARATOR.length(), oldPath)) {
        fileOrUrl = protocol + URLUtil.SCHEME_SEPARATOR + newPath + myUrl.substring(protocol.length() + URLUtil.SCHEME_SEPARATOR.length() + oldPath.length());
      }
    }
    for (FilePartNode child : children) {
      child.fixUrlPartNodes(oldPath, newPath);
    }
  }

  void replaceChildrenWithUPN() {
    children = ContainerUtil.map(children, n -> n.replaceWithUPN(this), EMPTY_ARRAY);
  }

  private @NotNull UrlPartNode replaceWithUPN(@NotNull FilePartNode parent) {
    if (this instanceof UrlPartNode) return (UrlPartNode)this;
    if (this instanceof FilePartNodeRoot) throw new IllegalArgumentException("invalid argument node: " + this);

    UrlPartNode newNode = new UrlPartNode(getName().toString(), parent.url(), fs);
    newNode.children = children;
    newNode.replaceChildrenWithUPN();
    processPointers(pointer -> newNode.addLeaf(pointer));

    leaves = null;

    return newNode;
  }

  @NotNull
  FilePartNode replaceWithFPPN(@NotNull VirtualFile file, @NotNull FilePartNode parent) {
    int nameId = getNameId(file);
    parent.children = ArrayUtil.remove(parent.children, this);
    FilePartNode newNode = parent.findChildByNameId(file, nameId, true, (NewVirtualFileSystem)file.getFileSystem());
    assert newNode.nameId == nameId;
    newNode.children = children; // old children are destroyed when renamed onto their parent
    processPointers(pointer-> newNode.addLeaf(pointer));

    leaves = null;

    return newNode;
  }

  static @NotNull String childUrl(@NotNull String parentUrl, @NotNull CharSequence childName, @NotNull NewVirtualFileSystem fs) {
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

  VirtualFilePointerImpl getPointer(@Nullable VirtualFilePointerListener listener) {
    Object leaves = this.leaves;
    if (leaves == null) {
      return null;
    }
    if (leaves instanceof VirtualFilePointerImpl leaf) {
      return leaf.myListener == listener ? leaf : null;
    }
    VirtualFilePointerImpl[] array = (VirtualFilePointerImpl[])leaves;
    for (VirtualFilePointerImpl pointer : array) {
      if (pointer.myListener == listener) return pointer;
    }
    return null;
  }

  void addAllPointersTo(@NotNull Collection<? super VirtualFilePointerImpl> outList) {
    processPointers(p->{ if (p.getNode() != null) outList.add(p); });
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

  void removeEmptyNodesByPath(@NotNull String path) {
    VfsImplUtil.PathFromRoot pair = VfsImplUtil.extractRootFromPath(fs, path);
    if (pair != null) {
      int rootIndex = binarySearchChildByName(pair.root().getNameSequence());
      if (rootIndex >= 0) {
        if (children[rootIndex].removeEmptyNodesByPath(FilePartNodeRoot.splitNames(pair.pathFromRoot()))) {
          children = children.length == 1 ? EMPTY_ARRAY : ArrayUtil.remove(children, rootIndex);
        }
      }
    }
    else {
      removeEmptyNodesByPath(FilePartNodeRoot.splitNames(path));
    }
  }

  private boolean removeEmptyNodesByPath(@NotNull List<String> parts) {
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

  boolean isCaseSensitive() {
    VirtualFile file = fileOrNull();
    return file == null ? fs.isCaseSensitive() : file.isCaseSensitive();
  }

  private void print(StringBuilder buffer, boolean recheck, String prefix) {
    buffer.append(prefix).append(" ").append(getName()).append(" isCaseSensitive:").append(isCaseSensitive());
    VirtualFile file = fileOrNull();
    if (recheck && file != null && fs instanceof LocalFileSystem) {
      buffer.append(" really parent sensitive: ").append(FileSystemUtil.readParentCaseSensitivity(new File(file.getPath())));
    }
    buffer.append("\n");
    for (FilePartNode child : children) {
      child.print(buffer, recheck, prefix + "  ");
    }
  }

  StringBuilder print(boolean recheck) {
    StringBuilder buffer = new StringBuilder();
    print(buffer, recheck,"");
    return buffer;
  }
}
