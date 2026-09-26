// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.ui.actions;

import com.intellij.vcs.log.VcsLogBundle;
import com.intellij.vcs.log.graph.PermanentGraph;
import com.intellij.vcs.log.graph.actions.GraphAction;
import com.intellij.vcs.log.impl.MainVcsLogUiProperties;
import com.intellij.vcs.log.ui.MainVcsLogUi;
import org.jetbrains.annotations.NotNull;

final class CollapseGraphAction extends CollapseOrExpandGraphAction {
  private static final GraphAction ourGraphAction = new GraphAction.GraphActionImpl(null, GraphAction.Type.BUTTON_COLLAPSE);

  CollapseGraphAction() {
    super(VcsLogBundle.messagePointer("action.title.collapse.linear.branches"),
          VcsLogBundle.messagePointer("action.description.collapse.linear.branches"),
          VcsLogBundle.messagePointer("action.title.collapse.merges"),
          VcsLogBundle.messagePointer("action.description.collapse.merges"));
  }

  @Override
  protected @NotNull GraphAction getGraphAction() {
    return ourGraphAction;
  }

  @Override
  protected void executeAction(@NotNull MainVcsLogUi vcsLogUi) {
    String title = vcsLogUi.getProperties().get(MainVcsLogUiProperties.GRAPH_OPTIONS) == PermanentGraph.Options.LinearBek.INSTANCE
                   ? VcsLogBundle.message("action.process.collapsing.merges")
                   : VcsLogBundle.message("action.process.collapsing.linear.branches");
    performLongAction(vcsLogUi, getGraphAction(), title);
  }
}
