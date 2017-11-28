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
package com.intellij.openapi.vcs.history;

import com.intellij.ui.dualView.DualTreeElement;
import com.intellij.util.TreeItem;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.tree.DefaultMutableTreeNode;
import java.util.List;

class TreeNodeOnVcsRevision extends DefaultMutableTreeNode implements DualTreeElement {
  @NotNull private final VcsFileRevision myRevision;

  public TreeNodeOnVcsRevision(@Nullable VcsFileRevision revision, @NotNull List<TreeItem<VcsFileRevision>> roots) {
    myRevision = revision == null ? VcsFileRevision.NULL : revision;
    for (TreeItem<VcsFileRevision> root : roots) {
      add(new TreeNodeOnVcsRevision(root.getData(), root.getChildren()));
    }
  }

  @NotNull
  public VcsFileRevision getRevision() {
    return myRevision;
  }

  public boolean shouldBeInTheFlatView() {
    return myRevision != VcsFileRevision.NULL;
  }

  public String toString() {
    return myRevision.getRevisionNumber().asString();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    TreeNodeOnVcsRevision that = (TreeNodeOnVcsRevision)o;

    if (!myRevision.getRevisionNumber().equals(that.myRevision.getRevisionNumber())) {
      return false;
    }

    return true;
  }

  @Override
  public int hashCode() {
    return myRevision.getRevisionNumber().hashCode();
  }
}
