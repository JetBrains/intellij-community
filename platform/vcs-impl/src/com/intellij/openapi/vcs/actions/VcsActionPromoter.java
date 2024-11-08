// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.actions;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.editor.actions.NextWordWithSelectionAction;
import com.intellij.openapi.editor.actions.PreviousWordWithSelectionAction;
import com.intellij.openapi.vcs.VcsDataKeys;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcs.commit.CommitActionsPanel;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

@ApiStatus.Internal
public final class VcsActionPromoter implements ActionPromoter {
  @Override
  public List<AnAction> promote(@NotNull List<? extends AnAction> actions, @NotNull DataContext context) {
    ActionManager am = ActionManager.getInstance();
    List<AnAction> reorderedActions = new ArrayList<>(actions);
    List<String> reorderedIds = ContainerUtil.map(reorderedActions, it -> am.getId(it));

    reorderActionPair(reorderedActions, reorderedIds, "Vcs.MoveChangedLinesToChangelist", "ChangesView.Move");
    reorderActionPair(reorderedActions, reorderedIds, "Vcs.RollbackChangedLines", "ChangesView.Revert");
    reorderActionPair(reorderedActions, reorderedIds, "Vcs.ShowDiffChangedLines", "Diff.ShowDiff");

    boolean isInMessageEditor = isCommitMessageEditor(context);
    for (AnAction action : actions) {
      boolean isMessageAction =
        action instanceof ShowMessageHistoryAction ||
        action instanceof PreviousWordWithSelectionAction ||
        action instanceof NextWordWithSelectionAction;
      boolean promote =
        isMessageAction && isInMessageEditor ||
        action instanceof CommitActionsPanel.DefaultCommitAction;
      if (promote) {
        reorderedActions.remove(action);
        reorderedActions.add(0, action);
      }
      else if (isMessageAction) {
        reorderedActions.remove(action);
        reorderedActions.add(action);
      }
    }
    return reorderedActions;
  }

  private static boolean isCommitMessageEditor(@NotNull DataContext context) {
    return context.getData(CommonDataKeys.EDITOR) != null &&
           context.getData(VcsDataKeys.COMMIT_MESSAGE_CONTROL) != null;
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
