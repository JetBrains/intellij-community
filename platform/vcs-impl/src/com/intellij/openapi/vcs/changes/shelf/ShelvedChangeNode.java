// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes.shelf;

import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.changes.ui.ChangesBrowserNode;
import com.intellij.openapi.vcs.changes.ui.ChangesBrowserNodeRenderer;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.PathUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;

import static com.intellij.util.FontUtil.spaceAndThinSpace;

class ShelvedChangeNode extends ChangesBrowserNode<ShelvedWrapper> implements Comparable<ShelvedChangeNode> {

  private final @NotNull ShelvedWrapper myShelvedChange;
  private final @NotNull FilePath myFilePath;
  private final @Nullable @Nls String myAdditionalText;

  protected ShelvedChangeNode(@NotNull ShelvedWrapper shelvedChange,
                              @NotNull FilePath filePath,
                              @Nullable @Nls String additionalText) {
    super(shelvedChange);
    myShelvedChange = shelvedChange;
    myFilePath = filePath;
    myAdditionalText = additionalText;
  }

  @Override
  public void render(@NotNull ChangesBrowserNodeRenderer renderer, boolean selected, boolean expanded, boolean hasFocus) {
    String path = myShelvedChange.getRequestName();
    String directory = StringUtil.defaultIfEmpty(PathUtil.getParentPath(path), VcsBundle.message("shelve.default.path.rendering"));
    String fileName = StringUtil.defaultIfEmpty(PathUtil.getFileName(path), path);

    renderer.append(fileName, new SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, myShelvedChange.getFileStatus().getColor()));
    if (myAdditionalText != null) {
      renderer.append(spaceAndThinSpace() + myAdditionalText, SimpleTextAttributes.REGULAR_ATTRIBUTES);
    }
    if (renderer.isShowFlatten()) {
      renderer.append(spaceAndThinSpace() + FileUtil.toSystemDependentName(directory), SimpleTextAttributes.GRAYED_ATTRIBUTES);
    }
    renderer.setIcon(FileTypeManager.getInstance().getFileTypeByFileName(fileName).getIcon());
  }

  @Override
  public String getTextPresentation() {
    return PathUtil.getFileName(myShelvedChange.getRequestName());
  }

  @Override
  protected boolean isFile() {
    return true;
  }

  @Override
  public int compareTo(@NotNull ShelvedChangeNode o) {
    return compareFilePaths(myFilePath, o.myFilePath);
  }

  @Override
  public @Nullable Color getBackgroundColor(@NotNull Project project) {
    return getBackgroundColorFor(project, myFilePath);
  }
}
