// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.filename;

import com.intellij.openapi.util.text.Strings;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;

public final class UniqueNameBuilder<T> {
  private static final String VFS_SEPARATOR = "/";
  private final Map<T, String> myPaths = new HashMap<>();
  private final String mySeparator;
  private final String myRoot;

  public UniqueNameBuilder(String root, String separator) {
    myRoot = root;
    mySeparator = separator;
  }

  public boolean contains(T file) {
    return myPaths.containsKey(file);
  }

  public int size() {
    return myPaths.size();
  }

  private static final class Node {
    final String myText;
    final HashMap<String, Node> myChildren;
    final Node myParentNode;
    int myNestedChildrenCount;

    Node(String text, Node parentNode) {
      myText = text;
      myParentNode = parentNode;
      myChildren = new HashMap<>();
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
    path = Strings.trimStart(path, myRoot);
    myPaths.put(key, path);

    Node current = myRootNode;
    Iterator<String> pathComponentsIterator = new PathComponentsIterator(path);

    while (pathComponentsIterator.hasNext()) {
      String word = pathComponentsIterator.next();
      current = current.findOrAddChild(word);
    }
    for (Node c = current; c != null; c = c.myParentNode) ++c.myNestedChildrenCount;
  }

  public String getShortPath(T key) {
    String path = myPaths.get(key);
    if (path == null) return key.toString();

    Node current = myRootNode;
    Node firstNodeWithBranches = null;
    Node firstNodeBeforeNodeWithBranches = null;
    Node fileNameNode = null;

    Iterator<String> pathComponentsIterator = new PathComponentsIterator(path);

    while (pathComponentsIterator.hasNext()) {
      String pathComponent = pathComponentsIterator.next();
      current = current.findOrAddChild(pathComponent);

      if (fileNameNode == null) fileNameNode = current;
      if (firstNodeBeforeNodeWithBranches == null &&
          firstNodeWithBranches != null &&
          current.myChildren.size() <= 1) {
        if (current.myParentNode.myNestedChildrenCount - current.myParentNode.myChildren.size() < 1) {
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
    for (Node c = firstNodeBeforeNodeWithBranches; c != myRootNode; c = c.myParentNode) {
      if (c != fileNameNode && c != firstNodeBeforeNodeWithBranches && c.myParentNode.myChildren.size() == 1) {
        b.append(mySeparator);
        b.append("\u2026");

        while (c.myParentNode != fileNameNode && c.myParentNode.myChildren.size() == 1) c = c.myParentNode;
      }
      else {
        if (c.myText.startsWith(VFS_SEPARATOR)) {
          if (!skipFirstSeparator) b.append(mySeparator);
          skipFirstSeparator = false;
          b.append(c.myText, VFS_SEPARATOR.length(), c.myText.length());
        }
        else {
          b.append(c.myText);
        }
      }
    }
    return b.toString();
  }

  public String getSeparator() {
    return mySeparator;
  }

  private static final class PathComponentsIterator implements Iterator<String> {
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
      }
      else {
        pathComponent = myPath.substring(0, myLastPos);
        if (!pathComponent.startsWith(VFS_SEPARATOR)) pathComponent = VFS_SEPARATOR + pathComponent;
        myLastPos = 0;
      }
      return pathComponent;
    }
  }
}
