/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;

import java.util.List;

// all file pointers we store in the tree with nodes corresponding to the file structure on disk
class FilePointerPartNode {
  private static final FilePointerPartNode[] EMPTY_ARRAY = new FilePointerPartNode[0];
  @NotNull private String part; // common prefix of all file pointers beneath
  @NotNull private FilePointerPartNode[] children;
  private FilePointerPartNode parent;
  VirtualFilePointerImpl leaf;  // file pointer for this exact path (e.g. concatenation of all "part" fields down from the root)
  // in case there is file pointer exists for this part, its info is saved here
  volatile Pair<VirtualFile, String> myFileAndUrl; // must not be both null
  private volatile long myLastUpdated = -1;
  volatile int useCount;

  private int pointersUnder = 1;   // number of alive pointers in this node plus all nodes beneath
  private static volatile boolean DEBUG = ApplicationManager.getApplication().isUnitTestMode();

  FilePointerPartNode(@NotNull String part, FilePointerPartNode parent, Pair<VirtualFile,String> fileAndUrl) {
    this.part = part;
    this.parent = parent;
    children = EMPTY_ARRAY;
    myFileAndUrl = fileAndUrl;
  }

  @Override
  public String toString() {
    return part + (children.length == 0 ? "" : " -> "+children.length);
  }

  boolean getPointersUnder(@NotNull String path, int start, @NotNull List<FilePointerPartNode> out) {
    checkStructure();
    if (pointersUnder == 0) return false;
    // invariant: upper nodes are matched
    int index = indexOfFirstDifferentChar(path, start);
    if (index - start == part.length() // part matched entirely, check children
       || index == path.length() // query matched entirely, add all children to matches
    ) {
      if (index == path.length() && leaf != null) {
        out.add(this);
      }

      for (FilePointerPartNode child : children) {
        child.getPointersUnder(path, index, out);
      }
    }
    // else there is no match
    return false;
  }

  void checkStructure() {
    if (!DEBUG) return;
    int childSum = 0;
    for (FilePointerPartNode child : children) {
      childSum += child.pointersUnder;
      child.checkStructure();
      assert child.parent == this;
    }
    if (leaf != null) childSum++;
    assert (useCount == 0) == (leaf == null) : useCount + " - " +leaf;
    assert pointersUnder == childSum : "expected: "+pointersUnder+"; actual: "+childSum;
  }

  @NotNull
  FilePointerPartNode findPointerOrCreate(@NotNull String path, int start, @NotNull Pair<VirtualFile, String> fileAndUrl) {
    // invariant: upper nodes are matched
    int index = indexOfFirstDifferentChar(path, start);
    if (index == path.length() // query matched entirely
      && index - start == part.length()
      )  {
      if (leaf == null) pointersUnder++;
      return this;
    }
    if (index - start == part.length() // part matched entirely, check children
      ) {
      for (FilePointerPartNode child : children) {
        // find the right child (its part should start with ours)
        int i = child.indexOfFirstDifferentChar(path, index);
        if (i != index && (i > index+1 || path.charAt(index) != '/')) {
          FilePointerPartNode node = child.findPointerOrCreate(path, index, fileAndUrl);
          if (node.leaf == null) pointersUnder++; // the new node's been created
          return node;
        }
      }
      // cannot insert to children, create child node manually
      String pathRest = path.substring(index);
      FilePointerPartNode newNode = new FilePointerPartNode(pathRest, this, fileAndUrl);
      children = ArrayUtil.append(children, newNode);
      pointersUnder++;
      return newNode;
    }
    // else there is no match
    // split
    // try to make "/" start the splitted part
    if (index > start && index != path.length() && path.charAt(index-1)== '/') index--;
    String pathRest = path.substring(index);
    FilePointerPartNode newNode = pathRest.isEmpty() ? this : new FilePointerPartNode(pathRest, this, fileAndUrl);
    String commonPredecessor = StringUtil.first(part, index - start, false);
    FilePointerPartNode splittedAway = new FilePointerPartNode(part.substring(index - start), this, myFileAndUrl);
    splittedAway.children = children;
    for (FilePointerPartNode child : children) {
      child.parent = splittedAway;
    }
    splittedAway.pointersUnder = pointersUnder;
    splittedAway.useCount = useCount;
    splittedAway.associate(leaf, myFileAndUrl);
    associate(null, null);
    useCount = 0;
    part = commonPredecessor;
    children = newNode == this ? new FilePointerPartNode[]{splittedAway} : new FilePointerPartNode[]{splittedAway, newNode};
    pointersUnder++;
    return newNode;
  }

  // return true if the root node must be deleted also
  boolean remove() {
    assert leaf != null : toString();
    associate(null, null);
    useCount = 0;
    myLastUpdated = -1;
    FilePointerPartNode node;
    for (node = this; node.parent != null; node = node.parent) {
      node.pointersUnder--;
    }
    if (--node.pointersUnder == 0) {
      node.children = EMPTY_ARRAY; // clear root node, especially in tests
      return true;
    }
    return false;
  }

  private int indexOfFirstDifferentChar(@NotNull String path, int start) {
    return indexOfFirstDifferentChar(path, start, part, 0, !SystemInfo.isFileSystemCaseSensitive);
  }

  @NotNull
  // returns pair.second != null always
  Pair<VirtualFile, String> update() {
    VirtualFileManager fileManager = VirtualFileManager.getInstance();

    long lastUpdated = myLastUpdated;
    Pair<VirtualFile, String> fileAndUrl = myFileAndUrl;
    long fsModCount = fileManager.getModificationCount();
    if (lastUpdated == fsModCount) return fileAndUrl;
    VirtualFile file = fileAndUrl.first;
    String url = fileAndUrl.second;

    if (url == null) {
      url = file.getUrl();
      if (!file.isValid()) file = null;
    }
    boolean fileIsValid = file != null && file.isValid();
    if (file != null && !fileIsValid) {
      file = null;
    }
    if (file == null) {
      file = fileManager.findFileByUrl(url);
      fileIsValid = file != null && file.isValid();
    }
    if (file != null) {
      if (fileIsValid) {
        url = file.getUrl(); // refresh url, it can differ
      }
      else {
        file = null; // can't find, try next time
      }
    }
    assert url != null;
    Pair<VirtualFile, String> result = Pair.create(file, url);
    myFileAndUrl = result;
    myLastUpdated = fsModCount;
    return result;
  }

  private static int indexOfFirstDifferentChar(@NotNull String s1, int start1, @NotNull String s2, int start2, boolean ignoreCase) {
    int len1 = s1.length();
    int len2 = s2.length();
    while (start1 < len1 && start2 < len2) {
      char c1 = s1.charAt(start1);
      char c2 = s2.charAt(start2);
      if (ignoreCase ? !StringUtil.charsEqualIgnoreCase(c1, c2) : c1 != c2) {
        return start1;
      }
      start1++;
      start2++;
    }
    return start1;
  }

  void associate(VirtualFilePointerImpl pointer, Pair<VirtualFile, String> fileAndUrl) {
    if (pointer != null) {
      pointer.myNode = this;
    }
    leaf = pointer;
    myFileAndUrl = fileAndUrl;
    myLastUpdated = -1;
  }

  int incrementUsageCount(int delta) {
    return useCount+=delta;
  }

  int getPointersUnder() {
    return pointersUnder;
  }

  @TestOnly
  public static void pushDebug(boolean newDebug, @NotNull Disposable disposable) {
    final boolean oldDebug = DEBUG;
    DEBUG = newDebug;
    Disposer.register(disposable, new Disposable() {
      @Override
      public void dispose() {
        DEBUG = oldDebug;
      }
    });
  }
}
