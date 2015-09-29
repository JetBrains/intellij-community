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
package com.intellij.openapi.roots.ui.util;

import com.intellij.openapi.vfs.VirtualFile;

import javax.swing.*;
import java.io.File;

public class ValidFileCellAppearance extends BaseTextCommentCellAppearance {
  private final VirtualFile myFile;

  public ValidFileCellAppearance(VirtualFile file) {
    myFile = file;
  }

  @Override
  protected Icon getIcon() {
    return myFile.getFileType().getIcon();
  }

  @Override
  protected String getSecondaryText() {
    return getSubname(true);
  }

  @Override
  protected String getPrimaryText() {
    return getSubname(false);
  }

  private String getSubname(boolean headOrTail) {
    String presentableUrl = myFile.getPresentableUrl();
    int separatorIndex = getSplitUrlIndex(presentableUrl);
    if (headOrTail)
      return separatorIndex >= 0 ? presentableUrl.substring(0, separatorIndex) : "";
    else
      return presentableUrl.substring(separatorIndex + 1);
  }

  protected int getSplitUrlIndex(String presentableUrl) {
    return presentableUrl.lastIndexOf(File.separatorChar);
  }

  public VirtualFile getFile() {
    return myFile;
  }
}
