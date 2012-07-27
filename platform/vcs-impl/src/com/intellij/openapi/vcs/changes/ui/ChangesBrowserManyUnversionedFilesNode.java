/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
import com.intellij.openapi.vcs.changes.UnversionedViewDialog;
import com.intellij.ui.SimpleTextAttributes;

public class ChangesBrowserManyUnversionedFilesNode extends ChangesBrowserNode {
  private final Project myProject;
  private final int myUnversionedSize;
  private final int myDirsSize;
  private final MyUnversionedShower myShower;

  public ChangesBrowserManyUnversionedFilesNode(Project project, int unversionedSize, int dirsSize) {
    super(UNVERSIONED_FILES_TAG);
    myProject = project;
    myUnversionedSize = unversionedSize;
    myDirsSize = dirsSize;
    myShower = new MyUnversionedShower(myProject);
  }

  public boolean canAcceptDrop(final ChangeListDragBean dragBean) {
    return false;
  }

  public void acceptDrop(final ChangeListOwner dragOwner, final ChangeListDragBean dragBean) {
  }

  @Override
  public void render(ChangesBrowserNodeRenderer renderer, boolean selected, boolean expanded, boolean hasFocus) {
    renderer.append(userObject.toString(), SimpleTextAttributes.REGULAR_ATTRIBUTES);
    final String s = " (" + (myDirsSize > 0 ? myDirsSize + " directories and " : "") + (myUnversionedSize - myDirsSize) + " files) ";
    renderer.append(s, SimpleTextAttributes.GRAY_ITALIC_ATTRIBUTES);
    renderer.append("Click to browse", SimpleTextAttributes.LINK_ATTRIBUTES, myShower);
  }

  private static class MyUnversionedShower implements Runnable {
    private final Project myProject;

    public MyUnversionedShower(Project project) {
      myProject = project;
    }

    public void run() {
      final UnversionedViewDialog dialog = new UnversionedViewDialog(myProject);
      dialog.show();
    }
  }
}
