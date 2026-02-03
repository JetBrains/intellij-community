// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.history;

import com.intellij.ui.dualView.DualTreeElement;
import com.intellij.util.TreeItem;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.tree.DefaultMutableTreeNode;
import java.util.List;

class TreeNodeOnVcsRevision extends DefaultMutableTreeNode implements DualTreeElement {
  private final @NotNull VcsFileRevision myRevision;

  TreeNodeOnVcsRevision(@Nullable VcsFileRevision revision, @NotNull List<TreeItem<VcsFileRevision>> roots) {
    myRevision = revision == null ? VcsFileRevision.NULL : revision;
    for (TreeItem<VcsFileRevision> root : roots) {
      add(new TreeNodeOnVcsRevision(root.getData(), root.getChildren()));
    }
  }

  public @NotNull VcsFileRevision getRevision() {
    return myRevision;
  }

  @Override
  public boolean shouldBeInTheFlatView() {
    return myRevision != VcsFileRevision.NULL;
  }

  @Override
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
