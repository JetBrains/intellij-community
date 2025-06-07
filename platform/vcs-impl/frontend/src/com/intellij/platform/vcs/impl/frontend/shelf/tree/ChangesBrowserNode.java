// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.vcs.impl.frontend.shelf.tree;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.openapi.util.UserDataHolderEx;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.containers.JBIterable;
import com.intellij.util.ui.tree.TreeUtil;
import com.intellij.platform.vcs.impl.frontend.VcsFrontendBundle;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.MutableTreeNode;
import java.util.function.ToIntFunction;

import static com.intellij.util.FontUtil.spaceAndThinSpace;

@ApiStatus.Internal
//copy-paste of com.intellij.openapi.vcs.changes.ui.ChangesBrowserNode.  We can't move it to shared code as it is used by external plugins.
public abstract class ChangesBrowserNode<T> extends DefaultMutableTreeNode implements UserDataHolderEx {

  private int myFileCount = -1;
  private int myDirectoryCount = -1;
  private UserDataHolderBase myUserDataHolder;

  protected ChangesBrowserNode(T userObject) {
    super(userObject);
  }


  @Override
  public ChangesBrowserNode<?> getParent() {
    return (ChangesBrowserNode<?>)super.getParent();
  }

  @Nullable
  @Override
  public <V> V getUserData(@NotNull Key<V> key) {
    UserDataHolderBase holder = myUserDataHolder;
    return holder == null ? null : holder.getUserData(key);
  }

  @Override
  public <V> void putUserData(@NotNull Key<V> key, @Nullable V value) {
    UserDataHolderBase holder = myUserDataHolder;
    if (holder == null) {
      myUserDataHolder = holder = new UserDataHolderBase();
    }
    holder.putUserData(key, value);
  }

  @NotNull
  @Override
  public <V> V putUserDataIfAbsent(@NotNull Key<V> key, @NotNull V value) {
    UserDataHolderBase holder = myUserDataHolder;
    if (holder == null) {
      myUserDataHolder = holder = new UserDataHolderBase();
    }
    return holder.putUserDataIfAbsent(key, value);
  }

  @Override
  public <V> boolean replace(@NotNull Key<V> key, @Nullable V oldValue, @Nullable V newValue) {
    UserDataHolderBase holder = myUserDataHolder;
    if (holder == null) {
      myUserDataHolder = holder = new UserDataHolderBase();
    }
    return holder.replace(key, oldValue, newValue);
  }

  @Override
  public void insert(MutableTreeNode newChild, int childIndex) {
    super.insert(newChild, childIndex);
    resetCounters();
  }

  @Override
  public void remove(int childIndex) {
    super.remove(childIndex);
    resetCounters();
  }

  protected boolean isFile() {
    return false;
  }

  protected boolean isDirectory() {
    return false;
  }

  public int getFileCount() {
    if (myFileCount == -1) {
      myFileCount = (isFile() ? 1 : 0) + sumForChildren(ChangesBrowserNode::getFileCount);
    }
    return myFileCount;
  }

  public int getDirectoryCount() {
    if (myDirectoryCount == -1) {
      myDirectoryCount = (isDirectory() ? 1 : 0) + sumForChildren(ChangesBrowserNode::getDirectoryCount);
    }
    return myDirectoryCount;
  }

  protected void resetCounters() {
    myFileCount = -1;
    myDirectoryCount = -1;
  }

  private int sumForChildren(@NotNull ToIntFunction<? super ChangesBrowserNode<?>> counter) {
    int sum = 0;
    for (int i = 0; i < getChildCount(); i++) {
      ChangesBrowserNode<?> child = (ChangesBrowserNode<?>)getChildAt(i);
      sum += counter.applyAsInt(child);
    }
    return sum;
  }

  public @NotNull JBIterable<ChangesBrowserNode<?>> traverse() {
    JBIterable<?> iterable = TreeUtil.treeNodeTraverser(this).preOrderDfsTraversal();
    //noinspection unchecked
    return (JBIterable<ChangesBrowserNode<?>>)iterable;
  }

  public void render(@NotNull JTree tree,
                     @NotNull ChangesBrowserNodeRenderer renderer,
                     boolean selected,
                     boolean expanded,
                     boolean hasFocus) {
    render(renderer, selected, expanded, hasFocus);
  }

  public void render(@NotNull ChangesBrowserNodeRenderer renderer, boolean selected, boolean expanded, boolean hasFocus) {
    renderer.append(getTextPresentation());
    appendCount(renderer);
  }

  @Nls
  @NotNull
  protected String getCountText() {
    int count = getFileCount();
    int dirCount = getDirectoryCount();
    String result = "";

    if (dirCount != 0 || count != 0) {
      result = spaceAndThinSpace() +
               (dirCount == 0
                ? VcsFrontendBundle.message("changes.nodetitle.changecount", count)
                : count == 0
                  ? VcsFrontendBundle.message("changes.nodetitle.directory.changecount", dirCount)
                  : VcsFrontendBundle.message("changes.nodetitle.directory.file.changecount", dirCount, count));
    }

    return result;
  }

  protected void appendCount(@NotNull ColoredTreeCellRenderer renderer) {
    renderer.append(getCountText(), SimpleTextAttributes.GRAYED_ATTRIBUTES);
  }

  @Override
  public void setUserObject(Object userObject) {
    if (userObject != getUserObject()) {
      Logger.getInstance(ChangesBrowserNode.class).error("Should not replace UserObject for ChangesBrowserNode");
    }
    super.setUserObject(userObject);
  }

  /**
   * Used by speedsearch, copy-to-clipboard and default renderer.
   */
  public @Nls String getTextPresentation() {
    return userObject == null ? "" : userObject.toString(); //NON-NLS
  }

  @Override
  public T getUserObject() {
    //noinspection unchecked
    return (T)userObject;
  }


  protected void appendParentPath(@NotNull ChangesBrowserNodeRenderer renderer, @Nullable String parentPath) {
    if (parentPath != null) {
      if (parentPath.isEmpty()) return;
      renderer.append(spaceAndThinSpace() + parentPath, SimpleTextAttributes.GRAYED_ATTRIBUTES);
    }
  }
}
