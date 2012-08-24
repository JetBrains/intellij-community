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
package com.intellij.cvsSupport2.cvsBrowser;

import com.intellij.openapi.vfs.VirtualFile;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.MutableTreeNode;
import javax.swing.tree.TreeNode;
import java.io.File;
import java.util.Collections;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.Vector;

public class CvsElement extends DefaultMutableTreeNode implements Comparable<CvsElement>{

  public static final CvsElement[] EMPTY_ARRAY = {};

  protected RemoteResourceDataProvider myDataProvider;
  protected String myPath;
  protected final String myName;
  private final Icon myIcon;
  private boolean myCanBeCheckedOut = true;
  private ChildrenLoader<CvsElement> myChildrenLoader;
  private boolean myLoading;

  public CvsElement(String name, Icon icon) {
    myName = name;
    myIcon = icon;
  }

  public void setChildrenLoader(ChildrenLoader<CvsElement> childrenLoader) {
    myChildrenLoader = childrenLoader;
  }

  public void setDataProvider(RemoteResourceDataProvider dataProvider) {
    myDataProvider = dataProvider;
  }

  public RemoteResourceDataProvider getDataProvider() {
    return myDataProvider;
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

  @Override
  public TreeNode getChildAt(int childIndex) {
    return (TreeNode)getMyChildren().get(childIndex);
  }

  @Override
  public int getChildCount() {
    return getMyChildren().size();
  }

  @Override
  public int getIndex(TreeNode node) {
    return getMyChildren().indexOf(node);
  }

  @Override
  public boolean getAllowsChildren() {
    if (children != null) {
      return getChildCount() > 0;
    }
    else {
      return !myDataProvider.equals(RemoteResourceDataProvider.NOT_EXPANDABLE);
    }
  }

  @Override
  public Enumeration children() {
    return getMyChildren().elements();
  }

  private Vector getMyChildren() {
    if (children == null) {
      myChildrenLoader.loadChildren(this);
    }
    return children;
  }

  public void insertSorted(MutableTreeNode newChild, Comparator comparator) {
    final int insertionPoint;
    if (children == null) {
      insert(newChild, 0);
    } else {
      insertionPoint = Collections.binarySearch(children, newChild, comparator);
      if (insertionPoint < 0) {
        insert(newChild, -insertionPoint - 1);
      }
    }
  }

  public String toString() {
    return myName;
  }

  public Icon getIcon() {
    return myIcon;
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

  public boolean isLoading() {
    return myLoading;
  }

  public void setLoading(boolean loading) {
    myLoading = loading;
  }
}
