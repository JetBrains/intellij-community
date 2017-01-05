/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

import com.intellij.ui.SimpleTextAttributes;
import org.jetbrains.annotations.NotNull;

public class ChangesBrowserSpecificFilesNode extends ChangesBrowserNode {
  protected final boolean myIsMany;
  @NotNull protected final Runnable myDialogShower;
  private final int myManyFileCount;
  private final int myManyDirectoryCount;

  protected ChangesBrowserSpecificFilesNode(Object userObject, int filesSize, int dirsSize, boolean many, @NotNull Runnable shower) {
    super(userObject);
    // if files presented in the same view recalculate number of dirs and files -> provide -1; otherwise use from model
    myManyFileCount = filesSize;
    myManyDirectoryCount = dirsSize;
    myIsMany = many;
    myDialogShower = shower;
  }


  @Override
  public void render(@NotNull ChangesBrowserNodeRenderer renderer, boolean selected, boolean expanded, boolean hasFocus) {
    super.render(renderer, selected, expanded, hasFocus);
    if (isManyFiles()) {
      renderer.append(" ", SimpleTextAttributes.REGULAR_ATTRIBUTES);
      renderer.append("browse", SimpleTextAttributes.LINK_ATTRIBUTES, myDialogShower);
    }
  }

  @Override
  public int getFileCount() {
    return myIsMany ? myManyFileCount : super.getFileCount();
  }

  @Override
  public int getDirectoryCount() {
    return myIsMany ? myManyDirectoryCount : super.getDirectoryCount();
  }

  public boolean isManyFiles() {
    return myIsMany;
  }
}
