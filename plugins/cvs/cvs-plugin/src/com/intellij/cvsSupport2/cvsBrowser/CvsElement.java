/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.cvsSupport2.cvsBrowser;

import com.intellij.openapi.vfs.VirtualFile;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.MutableTreeNode;
import javax.swing.tree.TreeNode;
import java.io.File;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Vector;

public class CvsElement extends DefaultMutableTreeNode implements Comparable<CvsElement>{

  protected RemoteResourceDataProvider myDataProvider;
  protected String myPath;
  protected final String myName;
  private final Icon myIcon;
  private final Icon myExpandedIcon;
  private boolean myCanBeCheckedOut = true;
  private ChildrenLoader myChildrenLoader;

  public CvsElement(String name, Icon icon, Icon expandedIcon) {
    myName = name;
    myIcon = icon;
    myExpandedIcon = expandedIcon;
  }

  public CvsElement(String name, Icon icon) {
    this(name, icon, icon);
  }

  public void setChildrenLoader(ChildrenLoader childrenLoader) {
    myChildrenLoader = childrenLoader;
  }

  public void setDataProvider(RemoteResourceDataProvider dataProvider) {
    myDataProvider = dataProvider;
  }

  public String getName() {
    return myName;
  }

  public void setPath(String path) {
    if (path.startsWith("./")) {
      myPath = path.substring(2);
    }
    else {
      myPath = path;
    }
  }

  public TreeNode getChildAt(int childIndex) {
    return (TreeNode)getMyChildren().get(childIndex);
  }

  public int getChildCount() {
    return getMyChildren().size();
  }

  public int getIndex(TreeNode node) {
    return getMyChildren().indexOf(node);
  }

  public boolean getAllowsChildren() {
    if (children != null) {
      return getChildCount() > 0;
    }
    else {
      return !myDataProvider.equals(RemoteResourceDataProvider.NOT_EXPANDABLE);
    }
  }

  public Enumeration children() {
    return getMyChildren().elements();
  }

  private Vector getMyChildren() {
    if (children == null) {
      myChildrenLoader.loadChildren(this, myPath, myDataProvider);
    }
    return children;
  }

  @Override
  public void insert(MutableTreeNode newChild, int childIndex) {
    if (!(newChild instanceof CvsElement)) {
      super.insert(newChild, childIndex);
      return;
    }
    final CvsElement cvsElement = (CvsElement)newChild;
    cvsElement.setPath(createPathForChild(cvsElement.myName));
    cvsElement.setChildrenLoader(myChildrenLoader);
    final int insertionPoint;
    if (children == null) {
      insertionPoint = -1;
    } else {
      final int toIndex = children.size() - 1; // skip loading node
      insertionPoint = Collections.binarySearch(children.subList(0, toIndex), newChild);
    }
    if (insertionPoint < 0) {
      super.insert(newChild, -(insertionPoint + 1));
    }
  }

  public String toString() {
    return myName;
  }

  public Icon getIcon(boolean expanded) {
    return expanded ? myExpandedIcon : myIcon;
  }

  public String getElementPath() {
    return myPath;
  }

  public String getCheckoutPath() {
    return getElementPath();
  }

  public boolean canBeCheckedOut() {
    return myCanBeCheckedOut;
  }

  public void cannotBeCheckedOut() {
    myCanBeCheckedOut = false;
  }

  public String getCheckoutDirectoryName() {
    return new File(getCheckoutPath()).getName();
  }

  public VirtualFile getVirtualFile() {
    return null;
  }

  public String createPathForChild(String name) {
    return getElementPath() + "/" + name;
  }

  public File getCvsLightFile() {
    return null;
  }

  @Override
  public int compareTo(CvsElement other) {
    final int result = myName.compareToIgnoreCase(other.myName);
    if (result != 0) {
      return result;
    }
    return myName.compareTo(other.myName);
  }
}
