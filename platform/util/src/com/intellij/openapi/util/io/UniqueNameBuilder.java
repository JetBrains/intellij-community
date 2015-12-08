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
import gnu.trove.TIntObjectHashMap;

import java.util.Map;

/**
 * @author yole
 */
public class UniqueNameBuilder<T> {
  public static final char INTERNAL_PATH_DELIMITER = '/';
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
    final char myChar;
    final TIntObjectHashMap<Node> myChildren;
    final Node myParentNode;

    Node(char ch, Node parentNode) {
      myChar = ch;
      myParentNode = parentNode;
      myChildren = new TIntObjectHashMap<Node>(1);
    }
  }

  private final Node myRootNode = new Node('\0', null);

  public void addPath(T key, String value) {
    value = StringUtil.trimStart(value, myRoot);
    myPaths.put(key, value);

    Node current = myRootNode;

    for (int i = value.length() - 1; i >= 0; --i) {
      char ch = value.charAt(i);
      Node node = current.myChildren.get(ch);
      if (node == null) current.myChildren.put(ch, node = new Node(ch, current));
      current = node;
    }
  }

  public String getShortPath(T key) {
    String path = myPaths.get(key);
    if (path == null) return key.toString();
    Node current = myRootNode, firstDirNodeWithSingleChildAfterNodeWithManyChildren = null;

    Node firstDirNode = null;

    boolean searchingForManyChildren = current.myChildren.size() == 1;
    for(int i = path.length() - 1; i >= 0; --i) {
      Node node = current.myChildren.get(path.charAt(i));
      if (node == null) return path;
      if (firstDirNode == null && node.myChar == INTERNAL_PATH_DELIMITER) {
        firstDirNode = node;
      }
      if (searchingForManyChildren && node.myChildren.size() > 1) {
        searchingForManyChildren = false;
      } else if (!searchingForManyChildren &&
                 firstDirNodeWithSingleChildAfterNodeWithManyChildren == null &&
                 node.myChildren.size() == 1 && node.myChar == INTERNAL_PATH_DELIMITER) {
        firstDirNodeWithSingleChildAfterNodeWithManyChildren = node;
      }
      current = node;
    }


    if (firstDirNodeWithSingleChildAfterNodeWithManyChildren == null) {
      firstDirNodeWithSingleChildAfterNodeWithManyChildren = current;
    }

    final boolean skipDirs = firstDirNodeWithSingleChildAfterNodeWithManyChildren != firstDirNode;

    StringBuilder b = new StringBuilder();
    final Node firstCharacterOfDirectoryName = firstDirNodeWithSingleChildAfterNodeWithManyChildren != current || current.myChar==
                                                                                                                  INTERNAL_PATH_DELIMITER
                ? firstDirNodeWithSingleChildAfterNodeWithManyChildren.myParentNode // firstDirNodeWithSingleChildAfterNodeWithManyChildren.myChar == /
                : firstDirNodeWithSingleChildAfterNodeWithManyChildren;
    for(Node n = firstCharacterOfDirectoryName; n != myRootNode; ) {
      if (n.myChar == INTERNAL_PATH_DELIMITER) b.append(mySeparator);
      else b.append(n.myChar);

      if (skipDirs && n.myChar == INTERNAL_PATH_DELIMITER && n != firstDirNode) {
        // Skip intermediate path content which is the same till file name
        n = n.myParentNode;
        while(n != firstDirNode) n = n.myParentNode;
        b.append("\u2026").append(mySeparator);
      }
      n = n.myParentNode;
    }
    return b.toString();
  }

  public String getSeparator() {
    return mySeparator;
  }
}
