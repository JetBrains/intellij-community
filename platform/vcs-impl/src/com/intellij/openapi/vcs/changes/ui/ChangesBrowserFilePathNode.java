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
        FilePath parentPath = path.getParentPath();
        appendParentPath(renderer, parentPath);
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
