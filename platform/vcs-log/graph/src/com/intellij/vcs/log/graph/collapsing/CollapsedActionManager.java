/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.vcs.log.graph.collapsing;

import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcs.log.graph.actions.GraphAction;
import com.intellij.vcs.log.graph.impl.facade.LinearGraphController.LinearGraphAction;
import com.intellij.vcs.log.graph.impl.facade.LinearGraphController.LinearGraphAnswer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

class CollapsedActionManager {

  private CollapsedActionManager() {}

  private interface ActionCase {
    @Nullable LinearGraphAnswer performAction(
      @NotNull CollapsedLinearGraphController graphController,
      @NotNull LinearGraphAction action
    );
  }

  @Nullable
  public static LinearGraphAnswer performAction(
    @NotNull CollapsedLinearGraphController graphController,
    @NotNull LinearGraphAction action
  ) {
    for (ActionCase actionCase : FILTER_ACTION_CASES) {
      LinearGraphAnswer graphAnswer = actionCase.performAction(graphController, action);
      if (graphAnswer != null)
        return graphAnswer;
    }
    return null;
  }

  private final static ActionCase LINEAR_COLLAPSE_CASE = new ActionCase() {
    @Nullable
    @Override
    public LinearGraphAnswer performAction(@NotNull CollapsedLinearGraphController graphController, @NotNull LinearGraphAction action) {
      if (action.getType() != GraphAction.Type.MOUSE_CLICK) return null;



      return null;
    }
  };

  private final static ActionCase LINEAR_EXPAND_CASE = new ActionCase() {
    @Nullable
    @Override
    public LinearGraphAnswer performAction(@NotNull CollapsedLinearGraphController graphController,
                                           @NotNull LinearGraphAction action) {
      if (action.getType() != GraphAction.Type.MOUSE_CLICK) return null;

      return null;
    }
  };

  private final static List<ActionCase> FILTER_ACTION_CASES = ContainerUtil.list(LINEAR_COLLAPSE_CASE, LINEAR_EXPAND_CASE);

}
