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
package com.intellij.openapi.diff.actions;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.diff.DiffBundle;
import com.intellij.openapi.diff.impl.DiffPanelImpl;
import com.intellij.openapi.diff.impl.highlighting.FragmentSide;
import com.intellij.openapi.project.DumbAwareAction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class MergeActionGroup extends ActionGroup {
  private final MergeOperations myOperations;

  public MergeActionGroup(DiffPanelImpl diffPanel, FragmentSide side) {
    myOperations = new MergeOperations(diffPanel, side);
  }

  @Override
  @NotNull
  public AnAction[] getChildren(@Nullable AnActionEvent e) {
    List<MergeOperations.Operation> operations = myOperations.getOperations();
    AnAction[] actions = new AnAction[operations.size() + 2];
    actions[0] = new SelectSuggestionAction(myOperations);
    actions[1] = Separator.getInstance();
    for (int i = 2; i < actions.length; i++) {
      actions[i] = new OperationAction(operations.get(i - 2));
    }
    return actions;
  }

  private static class SelectSuggestionAction extends AnAction {
    private final MergeOperations myOperations;

    public SelectSuggestionAction(MergeOperations operations) {
      super(DiffBundle.message("diff.dialog.select.change.action.name"),
            DiffBundle.message("diff.dialog.select.change.action.description"), null);
      myOperations = operations;
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      myOperations.selectSuggestion();
    }

    @Override
    public void update(AnActionEvent e) {
      e.getPresentation().setEnabled(myOperations.getCurrentFragment() != null);
    }
  }

  public static class OperationAction extends DumbAwareAction {
    private final MergeOperations.Operation myOperation;

    public OperationAction(MergeOperations.Operation operation) {
      super(operation.getName(), null, operation.getGutterIcon());
      myOperation = operation;
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      myOperation.perform(CommonDataKeys.PROJECT.getData(e.getDataContext()));
    }
  }
}
