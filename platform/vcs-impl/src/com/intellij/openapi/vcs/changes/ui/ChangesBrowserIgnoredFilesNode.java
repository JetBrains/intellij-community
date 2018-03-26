/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.changes.ChangeListOwner;
import com.intellij.openapi.vcs.changes.IgnoredViewDialog;
import org.jetbrains.annotations.NotNull;

public class ChangesBrowserIgnoredFilesNode extends ChangesBrowserSpecificFilesNode {

  private final boolean myUpdatingMode;

  protected ChangesBrowserIgnoredFilesNode(Project project, int filesSize, int dirsSize, boolean many, boolean updatingMode) {
    super(IGNORED_FILES_TAG, filesSize, dirsSize, many, () -> {
      if (!project.isDisposed()) new IgnoredViewDialog(project).show();
    });
    myUpdatingMode = updatingMode;
  }

  @Override
  public void render(@NotNull ChangesBrowserNodeRenderer renderer, boolean selected, boolean expanded, boolean hasFocus) {
    super.render(renderer, selected, expanded, hasFocus);
    if (myUpdatingMode) {
      appendUpdatingState(renderer);
    }
  }

  @Override
  public boolean canAcceptDrop(final ChangeListDragBean dragBean) {
    return dragBean.getUnversionedFiles().size() > 0;
  }

  @Override
  public void acceptDrop(final ChangeListOwner dragOwner, final ChangeListDragBean dragBean) {
    IgnoreUnversionedDialog.ignoreSelectedFiles(dragOwner.getProject(), dragBean.getUnversionedFiles());
  }

  @Override
  public int getSortWeight() {
    return IGNORED_SORT_WEIGHT;
  }
}