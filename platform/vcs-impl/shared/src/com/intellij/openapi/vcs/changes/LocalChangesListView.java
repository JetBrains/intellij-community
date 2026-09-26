// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes;

import com.intellij.ide.DefaultTreeExpander;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.changes.ui.ChangesBrowserNode;
import com.intellij.openapi.vcs.changes.ui.ChangesGroupingSupport;
import com.intellij.openapi.vcs.changes.ui.ChangesListView;
import com.intellij.openapi.vcs.changes.ui.HoverChangesTree;
import com.intellij.openapi.vcs.changes.ui.HoverIcon;
import com.intellij.openapi.vcs.changes.ui.NonSelectableNodeFilteringSelectionModel;
import com.intellij.ui.AnimatedIcon;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.JTree;

@ApiStatus.Internal
public class LocalChangesListView extends ChangesListView {
  public LocalChangesListView(@NotNull Project project) {
    super(project, false);
    putClientProperty(LOG_COMMIT_SESSION_EVENTS, true);
    putClientProperty(AnimatedIcon.ANIMATION_IN_RENDERER_ALLOWED, true);

    setTreeExpander(new MyTreeExpander(this));
    setSelectionModel(new NonSelectableNodeFilteringSelectionModel());

    new HoverChangesTree(this) {
      @Override
      public @Nullable HoverIcon getHoverIcon(@NotNull ChangesBrowserNode<?> node) {
        return ChangesViewNodeAction.EP_NAME.computeSafeIfAny(myProject, (it) -> it.createNodeHoverIcon(node));
      }
    }.install();
  }

  @Override
  protected @NotNull ChangesGroupingSupport installGroupingSupport() {
    // can't install support here - 'rebuildTree' is not defined
    return new ChangesGroupingSupport(myProject, this, myShowConflictsNode);
  }

  private static class MyTreeExpander extends DefaultTreeExpander {
    private MyTreeExpander(@NotNull JTree tree) {
      super(tree);
    }

    @Override
    protected void collapseAll(@NotNull JTree tree, int keepSelectionLevel) {
      super.collapseAll(tree, 2);
      TreeUtil.expand(tree, 1);
    }
  }
}
