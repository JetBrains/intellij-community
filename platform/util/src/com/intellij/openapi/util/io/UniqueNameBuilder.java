/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.openapi.util.io;

import com.intellij.openapi.util.text.StringUtil;
import gnu.trove.THashMap;

import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;

/**
 * @author yole
 */
public class UniqueNameBuilder<T> {
  private static final String VFS_SEPARATOR = "/";
  private final Map<T, String> myPaths = new THashMap<T, String>();
  private final String mySeparator;
  private final int myMaxLength;
  private final String myRoot;

  public UniqueNameBuilder(String root, String separator, int maxLength) {
    myRoot = root;
    mySeparator = separator;
    myMaxLength = maxLength;
  }

  public boolean contains(T file) {
    return myPaths.containsKey(file);
  }

  private static class Node {
    final String myText;
    final THashMap<String, Node> myChildren;
    final Node myParentNode;
    int myNestedChildrenCount;

    Node(String text, Node parentNode) {
      myText = text;
      myParentNode = parentNode;
      myChildren = new THashMap<String, Node>(1);
    }

    Node findOrAddChild(String word) {
      Node node = myChildren.get(word);
      if (node == null) myChildren.put(word, node = new Node(word, this));
      return node;
    }
  }

  private final Node myRootNode = new Node("", null);

  // Build a trie from path components starting from end
  // E.g. following try will be build from example
  //                                                                                   |<-------[/fabrique]  <-  [/idea]
  // /idea/pycharm/download/index.html                                                 |
  // /idea/fabrique/download/index.html           [RootNode] <- [/index.html] <- [/download] <- [/pycharm]  <- [/idea]
  // /idea/pycharm/documentation/index.html                              |
  //                                                                     |<------[/documentation] <- [/pycharm]  <- [/idea]
  public void addPath(T key, String path) {
    path = StringUtil.trimStart(path, myRoot);
    myPaths.put(key, path);

    Node current = myRootNode;
    Iterator<String> pathComponentsIterator = new PathComponentsIterator(path);

    while(pathComponentsIterator.hasNext()) {
      String word = pathComponentsIterator.next();
      current = current.findOrAddChild(word);
    }
    for(Node c = current; c != null; c = c.myParentNode) ++c.myNestedChildrenCount;
  }

  public String getShortPath(T key) {
    String path = myPaths.get(key);
    if (path == null) return key.toString();

    Node current = myRootNode;
    Node firstNodeWithBranches = null;
    Node firstNodeBeforeNodeWithBranches = null;
    Node fileNameNode = null;

    Iterator<String> pathComponentsIterator = new PathComponentsIterator(path);

    while(pathComponentsIterator.hasNext()) {
      String pathComponent = pathComponentsIterator.next();
      current = current.findOrAddChild(pathComponent);

      if (fileNameNode == null) fileNameNode = current;
      if (firstNodeBeforeNodeWithBranches == null &&
          firstNodeWithBranches != null &&
          current.myChildren.size() <= 1) {
        if(current.myParentNode.myNestedChildrenCount - current.myParentNode.myChildren.size() < 1) {
          firstNodeBeforeNodeWithBranches = current;
        }
      }
      
      if (current.myChildren.size() != 1 && firstNodeWithBranches == null) {
        firstNodeWithBranches = current;
      }
    }

    StringBuilder b = new StringBuilder();
    if (firstNodeBeforeNodeWithBranches == null) {
      firstNodeBeforeNodeWithBranches = current;
    }

    boolean skipFirstSeparator = true;
    for(Node c = firstNodeBeforeNodeWithBranches; c!= myRootNode; c = c.myParentNode) {
      if (c != fileNameNode && c != firstNodeBeforeNodeWithBranches && c.myParentNode.myChildren.size() == 1) {
        b.append(mySeparator);
        b.append("\u2026");

        while(c.myParentNode != fileNameNode && c.myParentNode.myChildren.size() == 1) c = c.myParentNode;
      } else {
        if (c.myText.startsWith(VFS_SEPARATOR)) {
          if (!skipFirstSeparator) b.append(mySeparator);
          skipFirstSeparator = false;
          b.append(c.myText, VFS_SEPARATOR.length(), c.myText.length());
        } else {
          b.append(c.myText);
        }
      }
    }
    return b.toString();
  }

  public String getSeparator() {
    return mySeparator;
  }

  private static class PathComponentsIterator implements Iterator<String> {
    private final String myPath;
    private int myLastPos;
    private int mySeparatorPos;

    PathComponentsIterator(String path) {
      myPath = path;
      myLastPos = path.length();
      mySeparatorPos = path.lastIndexOf(VFS_SEPARATOR);
    }

    @Override
    public boolean hasNext() {
      return myLastPos != 0;
    }

    @Override
    public String next() {
      if (myLastPos == 0) throw new NoSuchElementException();
      String pathComponent;
      
      if (mySeparatorPos != -1) {
        pathComponent = myPath.substring(mySeparatorPos, myLastPos);
        myLastPos = mySeparatorPos;
        mySeparatorPos = myPath.lastIndexOf(VFS_SEPARATOR, myLastPos - 1);
      } else {
        pathComponent = myPath.substring(0, myLastPos);
        if (!pathComponent.startsWith(VFS_SEPARATOR)) pathComponent = VFS_SEPARATOR + pathComponent;
        myLastPos = 0;
      }
      return pathComponent;
    }

    @Override
    public void remove() {
      throw new UnsupportedOperationException("remove");
    }
  }
}
