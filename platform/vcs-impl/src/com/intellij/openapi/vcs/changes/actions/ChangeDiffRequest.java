/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.openapi.vcs.changes.actions;

import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.diff.DiffRequest;
import com.intellij.openapi.diff.DiffTool;
import com.intellij.openapi.diff.DiffToolbar;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.NullableFactory;
import com.intellij.openapi.vcs.VcsDataKeys;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.ChangeRequestChain;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

/**
 * @author yole
 */
public class ChangeDiffRequest implements ChangeRequestChain {
  @NotNull private final List<DiffRequestPresentable> mySteps;
  private final boolean myShowFrame;
  private int myIndex;
  
  private final DiffExtendUIFactory myActionsFactory;

  private final AnAction myPrevChangeAction;
  private final AnAction myNextChangeAction;
  private final Project myProject;
  private final DiffChainContext myContext;
  private final AnAction mySelectChangeAction;

  public ChangeDiffRequest(final Project project, @NotNull List<DiffRequestPresentable> steps, final DiffExtendUIFactory actionsFactory,
                           final boolean showFrame) {
    myProject = project;
    mySteps = steps;
    myShowFrame = showFrame;
    myContext = new DiffChainContext();

    myIndex = 0;
    myActionsFactory = actionsFactory;

    myPrevChangeAction = ActionManager.getInstance().getAction("Diff.PrevChange");
    myNextChangeAction = ActionManager.getInstance().getAction("Diff.NextChange");
    mySelectChangeAction = ActionManager.getInstance().getAction("Diff.SelectedChange");
  }

  @NotNull
  @Override
  public List<DiffRequestPresentable> getAllRequests() {
    return mySteps;
  }

  private void onEveryMove(final DiffRequest simpleRequest, final boolean showFrame) {
    simpleRequest.passForDataContext(VcsDataKeys.DIFF_REQUEST_CHAIN, this);
    if (showFrame) {
      simpleRequest.addHint(DiffTool.HINT_SHOW_FRAME);
    }
    else {
      simpleRequest.addHint(DiffTool.HINT_SHOW_MODAL_DIALOG);
    }
    if (mySteps.size() > 1) {
      simpleRequest.addHint(DiffTool.HINT_ALLOW_NO_DIFFERENCES);
    }
  }

  public void quickCheckHaveStuff() throws VcsException {
    if (mySteps.isEmpty()) {
      throw new VcsException("Nothing selected to show diff");
    }
    if (mySteps.size() == 1) {
      mySteps.get(0).haveStuff();
    }
  }

  @Nullable
  public DiffRequest init(final int idx) {
    if (idx < 0 || idx > (mySteps.size() - 1)) return null;
    myIndex = idx - 1;
    final DiffRequest result = moveForward();
    if (result == null && idx > 0) {
      myIndex = -1;
      return moveForward();
    }
    return result;
  }

  public boolean canMoveForward() {
    return myIndex < (mySteps.size() - 1);
  }

  public boolean canMoveBack() {
    return myIndex > 0;
  }

  @Nullable
  public DiffRequest moveForward() {
    return moveWithErrorReport(new MoveDirection() {
      public boolean canMove() {
        return canMoveForward();
      }

      public int direction() {
        return 1;
      }
    });
  }

  @Nullable
  @Override
  public DiffRequest moveTo(DiffRequestPresentable presentable) {
    int index = findRequest(presentable);
    if (index == -1) {
      return null;
    }

    final List<String> errors = new ArrayList<>();
    DiffRequestPresentable.MyResult result = moveImpl(presentable, errors);
    showErrors(errors);
    if (result == null) {
      return null;
    }
    if (DiffPresentationReturnValue.removeFromList.equals(result.getReturnValue())) {
      return null;
    }
    myIndex = index;
    return result.getRequest();
  }

  @Nullable
  @Override
  public DiffRequestPresentable getCurrentRequest() {
    if (myIndex < 0 || myIndex >= mySteps.size()) {
      return null;
    }
    return mySteps.get(myIndex);
  }

  private int findRequest(DiffRequestPresentable presentable) {
    for (int i = 0; i < mySteps.size(); i++) {
      DiffRequestPresentable step = mySteps.get(i);
      if (step.equals(presentable)) {
        return i;
      }
    }
    return -1;
  }

  @Nullable
  private DiffRequest moveWithErrorReport(final MoveDirection moveDirection) {
    final List<String> errors = new ArrayList<>();
    final DiffRequest diffRequest = moveInc(moveDirection, errors);
    showErrors(errors);
    return diffRequest;
  }

  @Nullable
  private DiffRequestPresentable.MyResult moveImpl(DiffRequestPresentable requestPresentable, final List<String> errors) {
    final DiffRequestPresentable.MyResult result = requestPresentable.step(myContext);
    final DiffPresentationReturnValue returnValue = result.getReturnValue();
    if (DiffPresentationReturnValue.quit.equals(returnValue)) {
      errors.addAll(result.getErrors());
      return null;
    }

    if (DiffPresentationReturnValue.removeFromList.equals(returnValue)) {
      mySteps.remove(requestPresentable);
      errors.addAll(result.getErrors());
      return result;
    }

    final DiffRequest request = result.getRequest();
    takeStuffFromFactory(request, requestPresentable.createActions(myActionsFactory));
    onEveryMove(request, myShowFrame);
    return result;
  }

  @Nullable
  private DiffRequest moveInc(final MoveDirection moveDirection, final List<String> errors) {
    while (moveDirection.canMove()) {
      final int nextIdx = myIndex + moveDirection.direction();
      final DiffRequestPresentable diffRequestPresentable = mySteps.get(nextIdx);

      DiffRequestPresentable.MyResult result = moveImpl(diffRequestPresentable, errors);
      if (result == null) {
        return null;
      }
      if (DiffPresentationReturnValue.removeFromList.equals(result.getReturnValue())) {
        if (moveDirection.direction() < 0) {
          // our position moves to head
          myIndex += moveDirection.direction();
        }
        continue;
      }

      myIndex = nextIdx;
      return result.getRequest();
    }
    return null;
  }

  private void showErrors(final List<String> errors) {
    if (errors.isEmpty()) return;
    final StringBuilder sb = new StringBuilder("Following problems have occurred:\n\n");
    for (String error : errors) {
      sb.append(error).append('\n');
    }
    Messages.showErrorDialog(myProject, sb.toString(), "Show Diff");
  }

  private interface MoveDirection {
    boolean canMove();
    int direction();
  }

  @Nullable
  public DiffRequest moveBack() {
    return moveWithErrorReport(new MoveDirection() {
      public boolean canMove() {
        return canMoveBack();
      }

      public int direction() {
        return -1;
      }
    });
  }

  private void takeStuffFromFactory(final DiffRequest request, final List<? extends AnAction> actions) {
    if (mySteps.size() > 1 || (myActionsFactory != null)) {
      request.setToolbarAddons(new DiffRequest.ToolbarAddons() {
        public void customize(final DiffToolbar toolbar) {
          if (mySteps.size() > 1) {
            toolbar.addSeparator();
          }
          toolbar.addAction(myPrevChangeAction);
          toolbar.addAction(myNextChangeAction);
          toolbar.addAction(mySelectChangeAction);

          if (myActionsFactory != null) {
            toolbar.addSeparator();
            for (AnAction action : actions) {
              toolbar.addAction(action);
            }
          }
        }
      });
    }

    if (myActionsFactory != null) {
      request.setBottomComponentFactory(new NullableFactory<JComponent>() {
        public JComponent create() {
          return myActionsFactory.createBottomComponent();
        }
      });
    }
  }
}
