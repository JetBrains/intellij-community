// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.openapi.vcs.changes.ui;

import com.intellij.openapi.util.SystemInfoRt;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangesUtil;
import com.intellij.ui.SimpleTextAttributes;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author yole
 */
public class ChangesBrowserFilePathNode extends ChangesBrowserNode<FilePath> {
  public ChangesBrowserFilePathNode(FilePath userObject) {
    super(userObject);
  }

  @Override
  protected boolean isFile() {
    return !getUserObject().isDirectory();
  }

  @Override
  protected boolean isDirectory() {
    return getUserObject().isDirectory() && isLeaf();
  }

  @Override
  public void render(final ChangesBrowserNodeRenderer renderer, final boolean selected, final boolean expanded, final boolean hasFocus) {
    final FilePath path = (FilePath)userObject;
    if (path.isDirectory() || !isLeaf()) {
      renderer.append(getRelativePath(path), SimpleTextAttributes.REGULAR_ATTRIBUTES);
      if (!isLeaf()) {
        appendCount(renderer);
      }
    }
    else {
      if (renderer.isShowFlatten()) {
        renderer.append(path.getName(), SimpleTextAttributes.REGULAR_ATTRIBUTES);
        appendParentPath(renderer, path.getParentPath());
      }
      else {
        renderer.append(getRelativePath(path), SimpleTextAttributes.REGULAR_ATTRIBUTES);
      }
    }

    renderer.setIcon(path.getFileType(), path.isDirectory() || !isLeaf());
  }

  @NotNull
  protected String getRelativePath(FilePath path) {
    return getRelativePath(safeCastToFilePath((ChangesBrowserNode)getParent()), path);
  }

  @Override
  public String getTextPresentation() {
    return getRelativePath(getUserObject());
  }

  @Override
  public String toString() {
    return FileUtil.toSystemDependentName(getUserObject().getPath());
  }

  @Nullable
  public static FilePath safeCastToFilePath(ChangesBrowserNode node) {
    if (node instanceof ChangesBrowserModuleNode) {
      return ((ChangesBrowserModuleNode)node).getModuleRoot();
    }

    Object o = node.getUserObject();
    if (o instanceof FilePath) return (FilePath)o;
    if (o instanceof Change) {
      return ChangesUtil.getAfterPath((Change)o);
    }
    return null;
  }

  @NotNull
  public static String getRelativePath(@Nullable FilePath parent, @NotNull FilePath child) {
    boolean isLocal = !child.isNonLocal();
    boolean caseSensitive = isLocal && SystemInfoRt.isFileSystemCaseSensitive;
    String result = parent != null ? FileUtil.getRelativePath(parent.getPath(), child.getPath(), '/', caseSensitive) : null;

    result = result == null ? child.getPath() : result;

    return isLocal ? FileUtil.toSystemDependentName(result) : result;
  }

  public int getSortWeight() {
    if (((FilePath)userObject).isDirectory()) return DIRECTORY_PATH_SORT_WEIGHT;
    return FILE_PATH_SORT_WEIGHT;
  }

  public int compareUserObjects(final FilePath o2) {
    return getUserObject().getPath().compareToIgnoreCase(o2.getPath());
  }
}
