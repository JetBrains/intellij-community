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
package com.intellij.openapi.vcs.actions;

import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionPromoter;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.diff.actions.DiffWalkerAction;
import com.intellij.util.containers.ContainerUtil;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class VcsActionPromoter implements ActionPromoter {
  @Override
  public List<AnAction> promote(List<AnAction> actions, DataContext context) {
    ActionManager am = ActionManager.getInstance();
    List<AnAction> reorderedActions = new ArrayList<>(actions);
    List<String> reorderedIds = ContainerUtil.map(reorderedActions, it -> am.getId(it));

    reorderActionPair(reorderedActions, reorderedIds, "Vcs.MoveChangedLinesToChangelist", "ChangesView.Move");
    reorderActionPair(reorderedActions, reorderedIds, "Vcs.RollbackChangedLines", "ChangesView.Revert");
    reorderActionPair(reorderedActions, reorderedIds, "Vcs.Log.Refresh", "Refresh");

    Set<AnAction> promoted = new HashSet<>(ContainerUtil.filter(actions, action -> {
      return action instanceof ShowMessageHistoryAction ||
             action instanceof DiffWalkerAction;
    }));

    reorderedActions.removeAll(promoted);
    reorderedActions.addAll(0, promoted);

    return reorderedActions;
  }

  /**
   * Ensures that one global action has priority over another global action.
   * But is not pushing it ahead of other actions (ex: of some local action with same shortcut).
   */
  private static void reorderActionPair(List<AnAction> reorderedActions, List<String> reorderedIds,
                                       String highPriority, String lowPriority) {
    int highPriorityIndex = reorderedIds.indexOf(highPriority);
    int lowPriorityIndex = reorderedIds.indexOf(lowPriority);
    if (highPriorityIndex == -1 || lowPriorityIndex == -1) return;
    if (highPriorityIndex < lowPriorityIndex) return;

    String id = reorderedIds.remove(highPriorityIndex);
    AnAction action = reorderedActions.remove(highPriorityIndex);

    reorderedIds.add(lowPriorityIndex, id);
    reorderedActions.add(lowPriorityIndex, action);
  }
}
