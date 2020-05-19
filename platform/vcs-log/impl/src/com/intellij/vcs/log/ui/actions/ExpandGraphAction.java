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
package com.intellij.vcs.log.ui.actions;

import com.intellij.vcs.log.VcsLogBundle;
import com.intellij.vcs.log.graph.PermanentGraph;
import com.intellij.vcs.log.graph.actions.GraphAction;
import com.intellij.vcs.log.impl.MainVcsLogUiProperties;
import com.intellij.vcs.log.ui.MainVcsLogUi;
import org.jetbrains.annotations.NotNull;

public class ExpandGraphAction extends CollapseOrExpandGraphAction {

  private static final GraphAction ourGraphAction = new GraphAction.GraphActionImpl(null, GraphAction.Type.BUTTON_EXPAND);

  public ExpandGraphAction() {
    super(VcsLogBundle.messagePointer("action.title.expand.linear.branches"),
          VcsLogBundle.messagePointer("action.description.expand.linear.branches"),
          VcsLogBundle.messagePointer("action.title.expand.merges"),
          VcsLogBundle.messagePointer("action.description.expand.merges"));
  }

  @Override
  protected @NotNull GraphAction getGraphAction() {
    return ourGraphAction;
  }

  @Override
  protected void executeAction(@NotNull MainVcsLogUi vcsLogUi) {
    String title = vcsLogUi.getProperties().get(MainVcsLogUiProperties.BEK_SORT_TYPE) == PermanentGraph.SortType.LinearBek
                   ? VcsLogBundle.message("action.process.expanding.merges")
                   : VcsLogBundle.message("action.process.expanding.linear.branches");
    performLongAction(vcsLogUi, getGraphAction(), title);
  }
}
