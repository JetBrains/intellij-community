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
import com.intellij.util.PlatformIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.util.FontUtil.spaceAndThinSpace;

/**
 * @author yole
 */
public class ChangesBrowserFilePathNode extends ChangesBrowserNode<FilePath> {
  public ChangesBrowserFilePathNode(FilePath userObject) {
    super(userObject);
    if (!userObject.isDirectory()) {
      myCount = 1;
    }
  }

  @Override
  protected boolean isDirectory() {
    return getUserObject().isDirectory() && isLeaf();
  }

  @Override
  public void render(final ChangesBrowserNodeRenderer renderer, final boolean selected, final boolean expanded, final boolean hasFocus) {
    final FilePath path = (FilePath)userObject;
    if (path.isDirectory() || !isLeaf()) {
      renderer.append(getRelativePath(safeCastToFilePath(((ChangesBrowserNode)getParent()).getUserObject()), path),
             SimpleTextAttributes.REGULAR_ATTRIBUTES);
      if (!isLeaf()) {
        appendCount(renderer);
      }
      renderer.setIcon(PlatformIcons.DIRECTORY_CLOSED_ICON);
    }
    else {
      if (renderer.isShowFlatten()) {
        renderer.append(path.getName(), SimpleTextAttributes.REGULAR_ATTRIBUTES);
        FilePath parentPath = path.getParentPath();
        renderer.append(spaceAndThinSpace() + FileUtil.getLocationRelativeToUserHome(parentPath.getPresentableUrl()), SimpleTextAttributes.GRAYED_ATTRIBUTES);
      }
      else {
        renderer.append(getRelativePath(safeCastToFilePath(((ChangesBrowserNode)getParent()).getUserObject()), path),
                        SimpleTextAttributes.REGULAR_ATTRIBUTES);
      }
      renderer.setIcon(path.getFileType().getIcon());
    }
  }

  @Override
  public String getTextPresentation() {
    return getUserObject().getName();
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
    if (((FilePath)userObject).isDirectory()) return 4;
    return 5;
  }

  public int compareUserObjects(final Object o2) {
    if (o2 instanceof FilePath) {
      return getUserObject().getPath().compareToIgnoreCase(((FilePath)o2).getPath());
    }

    return 0;
  }
}
