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

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.changes.UnversionedViewDialog;
import com.intellij.ui.SimpleTextAttributes;
import org.jetbrains.annotations.NotNull;

public class ChangesBrowserUnversionedFilesNode extends ChangesBrowserNode {

  private final int myUnversionedSize;
  private final int myUnversionedDirsSize;
  private boolean myManyUnversioned;
  @NotNull private final Runnable myDialogShower;

  public ChangesBrowserUnversionedFilesNode(@NotNull Project project,
                                            int unversionedSize,
                                            int unversionedDirsSize,
                                            boolean manyUnversioned) {
    super(UNVERSIONED_FILES_TAG);
    myUnversionedSize = unversionedSize;
    myUnversionedDirsSize = unversionedDirsSize;
    myManyUnversioned = manyUnversioned;
    myDialogShower = () -> new UnversionedViewDialog(project).show();
  }

  public int getUnversionedSize() {
    return myUnversionedSize;
  }

  @Override
  public void render(ChangesBrowserNodeRenderer renderer, boolean selected, boolean expanded, boolean hasFocus) {
    super.render(renderer, selected, expanded, hasFocus);
    if (isManyUnversioned()) {
      renderer.append(" ", SimpleTextAttributes.REGULAR_ATTRIBUTES);
      renderer.append("browse", SimpleTextAttributes.LINK_ATTRIBUTES, myDialogShower);
    }
  }

  @Override
  public int getCount() {
    return myUnversionedSize - myUnversionedDirsSize;
  }

  @Override
  public int getDirectoryCount() {
    return myUnversionedDirsSize;
  }

  public boolean isManyUnversioned() {
    return myManyUnversioned;
  }

  @Override
  public int getSortWeight() {
    return 8;
  }
}
