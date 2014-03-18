/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.vcs.log.ui.render;

import com.intellij.vcs.log.data.VcsLogDataHolder;
import com.intellij.vcs.log.graph.GraphFacade;
import com.intellij.vcs.log.graph.PaintInfo;
import com.intellij.vcs.log.ui.VcsLogColorManager;
import com.intellij.vcs.log.ui.frame.VcsLogGraphTable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class GraphCommitCellRender extends AbstractPaddingCellRender {

  @NotNull private GraphFacade myGraphFacade;

  public GraphCommitCellRender(@NotNull VcsLogColorManager colorManager, @NotNull VcsLogDataHolder dataHolder,
                               @NotNull GraphFacade graphFacade, @NotNull VcsLogGraphTable table) {
    super(colorManager, dataHolder, table);
    myGraphFacade = graphFacade;
  }

  @Nullable
  @Override
  protected PaintInfo getGraphImage(int row) {
    return myGraphFacade.paint(row);
  }

  public void updateGraphFacade(@NotNull GraphFacade graphFacade) {
    myGraphFacade = graphFacade;
  }

}
