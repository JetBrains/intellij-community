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

import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.util.SystemInfoRt;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangesUtil;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.vcsUtil.VcsUtil;
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
        appendParentPath(renderer, parentPath.getPresentableUrl());
      }
      else {
        renderer.append(getRelativePath(path), SimpleTextAttributes.REGULAR_ATTRIBUTES);
      }
    }

    renderer.setIcon(path.getFileType(), path.isDirectory() || !isLeaf());
  }

  @NotNull
  protected String getRelativePath(FilePath path) {
    return getRelativePath(safeCastToFilePath(((ChangesBrowserNode)getParent()).getUserObject()), path);
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
  public static FilePath safeCastToFilePath(Object o) {
    if (o instanceof FilePath) return (FilePath)o;
    if (o instanceof Change) {
      return ChangesUtil.getAfterPath((Change)o);
    }
    if (o instanceof Module) {
      return VcsUtil.getFilePath(ModuleUtilCore.getModuleDirPath((Module)o));
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

  public int compareUserObjects(final Object o2) {
    if (o2 instanceof FilePath) {
      return getUserObject().getPath().compareToIgnoreCase(((FilePath)o2).getPath());
    }

    return 0;
  }
}
