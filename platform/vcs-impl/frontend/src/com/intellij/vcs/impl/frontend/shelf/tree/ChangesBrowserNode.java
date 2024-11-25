// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.impl.frontend.shelf.tree;

import com.intellij.diagnostic.PluginException;
import com.intellij.ide.util.treeView.FileNameComparator;
import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.openapi.util.UserDataHolderEx;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.changes.ChangesUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.VfsPresentationUtil;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.DirtyUI;
import com.intellij.ui.JBColor;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.ObjectUtils;
import com.intellij.util.SlowOperations;
import com.intellij.util.containers.Convertor;
import com.intellij.util.containers.JBIterable;
import com.intellij.util.ui.tree.TreeUtil;
import com.intellij.vcsUtil.VcsUtil;
import org.jetbrains.annotations.*;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.MutableTreeNode;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.util.List;
import java.util.Objects;
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

  public final void markAsHelperNode() {
    myHelper = true;
  }

  public boolean isMeaningfulNode() {
    return !myHelper;
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

  @NotNull
  public <U> List<U> getAllObjectsUnder(@NotNull Class<U> clazz) {
    return traverseObjectsUnder().filter(clazz).toList();
  }

  public @NotNull JBIterable<?> traverseObjectsUnder() {
    return traverse().map(TreeUtil::getUserObject);
  }

  public @NotNull JBIterable<ChangesBrowserNode<?>> traverse() {
    JBIterable<?> iterable = TreeUtil.treeNodeTraverser(this).preOrderDfsTraversal();
    //noinspection unchecked
    return (JBIterable<ChangesBrowserNode<?>>)iterable;
  }

  public @NotNull JBIterable<ChangesBrowserNode<?>> iterateNodeChildren() {
    JBIterable<?> iterable = TreeUtil.nodeChildren(this);
    //noinspection unchecked
    return (JBIterable<ChangesBrowserNode<?>>)iterable;
  }

  public @NotNull JBIterable<VirtualFile> iterateFilesUnder() {
    return traverseObjectsUnder().filter(VirtualFile.class).filter(VirtualFile::isValid);
  }

  public @NotNull JBIterable<FilePath> iterateFilePathsUnder() {
    return traverse()
      .filter(ChangesBrowserNode::isLeaf)
      .map(ChangesBrowserNode::getUserObject)
      .filter(FilePath.class);
  }

  public void render(@NotNull JTree tree, @NotNull ChangesBrowserNodeRenderer renderer, boolean selected, boolean expanded, boolean hasFocus) {
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
                ? VcsBundle.message("changes.nodetitle.changecount", count)
                : count == 0
                  ? VcsBundle.message("changes.nodetitle.directory.changecount", dirCount)
                  : VcsBundle.message("changes.nodetitle.directory.file.changecount", dirCount, count));
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
    PluginException.reportDeprecatedDefault(getClass(), "getTextPresentation", "A proper implementation required");
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

  protected void appendParentPath(@NotNull ChangesBrowserNodeRenderer renderer, @Nullable VirtualFile parentPath) {
    if (parentPath != null) {
      String presentablePath = VcsUtil.getPresentablePath(renderer.getProject(), parentPath, true, true);
      if (presentablePath.isEmpty()) return;
      renderer.append(spaceAndThinSpace() + presentablePath, SimpleTextAttributes.GRAYED_ATTRIBUTES);
    }
  }

  protected void appendUpdatingState(@NotNull ChangesBrowserNodeRenderer renderer) {
    renderer.append((getCountText().isEmpty() ? spaceAndThinSpace() : ", ") + VcsBundle.message("changes.nodetitle.updating"),
                    SimpleTextAttributes.GRAYED_ATTRIBUTES);
  }

  @DirtyUI
  @Nullable
  protected static Color getBackgroundColorFor(@NotNull Project project, @Nullable Object object) {
    VirtualFile file;//TODO
    return null;
  }
}
