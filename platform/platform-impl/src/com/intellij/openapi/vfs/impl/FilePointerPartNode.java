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

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.impl.ApplicationInfoImpl;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.newvfs.ManagingFS;
import com.intellij.openapi.vfs.pointers.VirtualFilePointer;
import com.intellij.openapi.vfs.pointers.VirtualFilePointerManager;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

// all file pointers we store in the tree with nodes corresponding to the file structure on disk
class FilePointerPartNode {
  private static final FilePointerPartNode[] EMPTY_ARRAY = new FilePointerPartNode[0];
  @NotNull String part; // common prefix of all file pointers beneath
  @NotNull FilePointerPartNode[] children;
  FilePointerPartNode parent;
  // file pointers for this exact path (e.g. concatenation of all "part" fields down from the root).
  // Either VirtualFilePointerImpl or VirtualFilePointerImpl[] (when it so happened that several pointers merged into one node - e.g. after file rename onto existing pointer)
  private Object leaves;

  // in case there is file pointer exists for this part, its info is saved here
  volatile Pair<VirtualFile, String> myFileAndUrl; // must not be both null
  volatile long myLastUpdated = -1;
  volatile int useCount;

  int pointersUnder;   // number of alive pointers in this node plus all nodes beneath
  private static final VirtualFileManager ourFileManager = VirtualFileManager.getInstance();

  FilePointerPartNode(@NotNull String part, FilePointerPartNode parent, Pair<VirtualFile,String> fileAndUrl, int pointersToStore) {
    this.part = part;
    this.parent = parent;
    children = EMPTY_ARRAY;
    myFileAndUrl = fileAndUrl;
    pointersUnder = pointersToStore;
  }

  @Override
  public String toString() {
    return part + (children.length == 0 ? "" : " -> "+children.length);
  }

  // tries to match the VirtualFile path hierarchy with the trie structure of FilePointerPartNodes
  // returns the node (in outNode[0]) and length of matched characters in that node, or -1 if there is no match
  // recursive nodes (i.e. the nodes containing VFP with recursive==true) will be added to outDirs
  private int position(@Nullable VirtualFile parent,
                       @Nullable CharSequence parentName,
                       boolean separator,
                       @NotNull CharSequence childName, int childStart, int childEnd,
                       @NotNull FilePointerPartNode[] outNode,
                       @Nullable List<FilePointerPartNode> outDirs) {
    int partStart;
    if (parent == null) {
      partStart = 0;
      outNode[0] = this;
    }
    else {
      VirtualFile gParent = parent.getParent();
      CharSequence gParentName = gParent == null ? null : gParent.getNameSequence();
      boolean gSeparator = gParentName != null && !StringUtil.equals(gParentName, "/");
      partStart = position(gParent, gParentName, gSeparator, parentName, 0, parentName.length(), outNode, outDirs);
      if (partStart == -1) return -1;
    }

    FilePointerPartNode found = outNode[0];

    boolean childSeparator = false;
    if (separator) {
      if (partStart == found.part.length()) {
        childSeparator = true;
      }
      else {
        int sepIndex = indexOfFirstDifferentChar("/", 0, found.part, partStart);
        if (sepIndex != 1) return -1;
        partStart++;
      }
    }
    int index = indexOfFirstDifferentChar(childName, childStart, found.part, partStart);

    if (index == childEnd) {
      addRecursiveDirectoryPtr(outDirs);
      return partStart + childEnd - childStart;
    }

    if (partStart + index-childStart == found.part.length()) {
      // go to children
      for (FilePointerPartNode child : found.children) {
        // do not accidentally modify outDirs
        int childPos = child.position(null, null, childSeparator, childName, index, childEnd, outNode, null);
        if (childPos != -1) {
          addRecursiveDirectoryPtr(outDirs);

          return childPos;
        }
      }
    }
    // else there is no match
    return -1;
  }

  private void addRecursiveDirectoryPtr(@Nullable List<FilePointerPartNode> dirs) {
    if(dirs != null && hasRecursiveDirectoryPointer() && (dirs.isEmpty() || dirs.get(dirs.size()-1) != this)) {
      dirs.add(this);
    }
  }

  // appends to "out" all nodes under this node whose path (beginning from this node) starts in prefix.subSequence(start), then parent.getPath(), then childName
  void addRelevantPointersFrom(@Nullable VirtualFile parent,
                               boolean separator,
                               @NotNull CharSequence childName,
                               @NotNull List<FilePointerPartNode> out) {
    CharSequence parentName = parent == null ? null : parent.getNameSequence();
    FilePointerPartNode[] outNode = new FilePointerPartNode[1];
    int position = position(parent, parentName, separator, childName, 0, childName.length(), outNode, out);
    if (position != -1) {
      FilePointerPartNode node = outNode[0];
      addAllPointersUnder(node, out);
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

  private static void addAllPointersUnder(@NotNull FilePointerPartNode node, @NotNull List<FilePointerPartNode> out) {
    if (node.leaves != null) {
      out.add(node);
    }
    for (FilePointerPartNode child : node.children) {
      addAllPointersUnder(child, out);
    }
  }

  private static final boolean UNIT_TEST = ApplicationManager.getApplication().isUnitTestMode();
  void checkConsistency() {
    if (UNIT_TEST && !ApplicationInfoImpl.isInStressTest()) {
      doCheckConsistency(false);
    }
  }

  private void doCheckConsistency(boolean dotDotOccurred) {
    int ddi = part.indexOf("..");
    if (ddi != -1) {
      // part must not contain "/.." nor "../" nor be just ".."
      // (except when the pointer was created from URL of non-existing file with ".." inside)
      dotDotOccurred |= part.equals("..") || ddi != 0 && part.charAt(ddi-1) == '/' || ddi < part.length() - 2 && part.charAt(ddi+2) == '/';
    }
    int childSum = 0;
    for (FilePointerPartNode child : children) {
      childSum += child.pointersUnder;
      child.doCheckConsistency(dotDotOccurred);
      assert child.parent == this;
    }
    childSum += leavesNumber();
    assert (useCount == 0) == (leaves == null) : useCount + " - " + (leaves instanceof VirtualFilePointerImpl ? leaves : Arrays.toString((VirtualFilePointerImpl[])leaves));
    assert pointersUnder == childSum : "expected: "+pointersUnder+"; actual: "+childSum;
    Pair<VirtualFile, String> fileAndUrl = myFileAndUrl;
    if (fileAndUrl != null && fileAndUrl.second != null) {
      String url = fileAndUrl.second;
      assert endsWith(url, part) : "part is: '" + part + "' but url is: '" + url + "'";
    }
    boolean hasFile = fileAndUrl != null && fileAndUrl.first != null;

    // when the node contains real file its path should be canonical
    assert !hasFile || !dotDotOccurred : "Path is not canonical: '"+getUrl()+"'; my part: '"+part+"'";
  }

  @NotNull
  FilePointerPartNode findPointerOrCreate(@NotNull String path,
                                          int start,
                                          @NotNull Pair<VirtualFile, String> fileAndUrl,
                                          int pointersToStore) {
    // invariant: upper nodes are matched
    int index = indexOfFirstDifferentChar(path, start);
    if (index == path.length() // query matched entirely
      && index - start == part.length()) {
      if (leaves == null) {
        pointersUnder += pointersToStore; // the pointer is going to be written here
      }
      return this;
    }
    if (index - start == part.length() // part matched entirely, check children
      ) {
      for (FilePointerPartNode child : children) {
        // find the right child (its part should start with ours)
        int i = child.indexOfFirstDifferentChar(path, index);
        if (i != index && (i > index+1 || path.charAt(index) != '/' || index == 0)) {
          FilePointerPartNode node = child.findPointerOrCreate(path, index, fileAndUrl, pointersToStore);
          if (node.leaves == null) {
            pointersUnder += pointersToStore; // the new node's been created
          }
          return node;
        }
      }
      // cannot insert to children, create child node manually
      String pathRest = path.substring(index);
      FilePointerPartNode newNode = new FilePointerPartNode(pathRest, this, fileAndUrl, pointersToStore);
      children = ArrayUtil.append(children, newNode);
      pointersUnder += pointersToStore;
      return newNode;
    }
    // else there is no match, split
    // try to make "/" start the splitted part
    if (index > start + 1 && index != path.length() && path.charAt(index - 1) == '/') index--;
    String pathRest = path.substring(index);
    FilePointerPartNode newNode = pathRest.isEmpty() ? this : new FilePointerPartNode(pathRest, this, fileAndUrl, pointersToStore);
    String commonPredecessor = StringUtil.first(part, index - start, false);
    FilePointerPartNode splittedAway = new FilePointerPartNode(part.substring(index - start), this, myFileAndUrl, pointersUnder);
    splittedAway.children = children;
    for (FilePointerPartNode child : children) {
      child.parent = splittedAway;
    }
    splittedAway.useCount = useCount;
    splittedAway.associate(leaves, myFileAndUrl);
    useCount = 0;
    part = commonPredecessor;
    children = newNode == this ? new FilePointerPartNode[]{splittedAway} : new FilePointerPartNode[]{splittedAway, newNode};
    pointersUnder+=pointersToStore;
    associate(null, null);
    return newNode;
  }

  // returns root node
  @NotNull
  FilePointerPartNode remove() {
    int pointersNumber = leavesNumber();
    assert leaves != null : toString();
    associate(null, null);
    useCount = 0;
    myLastUpdated = -1;
    FilePointerPartNode node;
    for (node = this; node.parent != null; node = node.parent) {
      node.pointersUnder-=pointersNumber;
    }
    if ((node.pointersUnder-=pointersNumber) == 0) {
      node.children = EMPTY_ARRAY; // clear root node, especially in tests
    }
    return node;
  }

  private int indexOfFirstDifferentChar(@NotNull CharSequence path, int start) {
    return indexOfFirstDifferentChar(path, start, part, 0);
  }

  private static boolean endsWith(@NotNull String string, @NotNull String end) {
    return indexOfFirstDifferentChar(string, string.length() - end.length(), end, 0) == string.length();
  }

  @Nullable("null means this node's myFileAndUrl became invalid (e.g. after splitting into two other nodes)")
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
      result = Pair.create(file, url);
      synchronized (VirtualFilePointerManager.getInstance()) {
        Pair<VirtualFile, String> storedFileAndUrl = myFileAndUrl;
        if (storedFileAndUrl == null || storedFileAndUrl != fileAndUrl) return null; // somebody splitted this node in the meantime, try to re-compute
        myFileAndUrl = result;
      }
    }
    else {
      result = fileAndUrl;
    }
    myLastUpdated = fsModCount; // must be the last
    return result;
  }

  // return an index in s1 of the first different char of strings s1[start1..) and s2[start2..)
  private static int indexOfFirstDifferentChar(@NotNull CharSequence s1, int start1, @NotNull String s2, int start2) {
    boolean ignoreCase = !SystemInfo.isFileSystemCaseSensitive;
    int len1 = s1.length();
    int len2 = s2.length();
    while (start1 < len1 && start2 < len2) {
      char c1 = s1.charAt(start1);
      char c2 = s2.charAt(start2);
      if (!StringUtil.charsMatch(c1, c2, ignoreCase)) {
        return start1;
      }
      start1++;
      start2++;
    }
    return start1;
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

  private String getUrl() {
    return parent == null ? part : parent.getUrl() + part;
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
}
