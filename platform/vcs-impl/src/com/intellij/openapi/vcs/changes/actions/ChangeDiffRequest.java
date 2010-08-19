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
import com.intellij.openapi.util.NullableFactory;
import com.intellij.openapi.vcs.VcsDataKeys;
import com.intellij.openapi.vcs.changes.ChangeRequestChain;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.List;

/**
 * @author yole
 */
public class ChangeDiffRequest implements ChangeRequestChain {
  private final List<DiffRequestPresentable> mySteps;
  private final boolean myShowFrame;
  private int myIndex;
  
  private final ShowDiffAction.DiffExtendUIFactory myActionsFactory;

  private final AnAction myPrevChangeAction;
  private final AnAction myNextChangeAction;
  private final Project myProject;
  private final DiffChainContext myContext;

  public ChangeDiffRequest(final Project project, final List<DiffRequestPresentable> steps, final ShowDiffAction.DiffExtendUIFactory actionsFactory,
                           final boolean showFrame) {
    myProject = project;
    mySteps = steps;
    myShowFrame = showFrame;
    myContext = new DiffChainContext();

    myIndex = 0;
    myActionsFactory = actionsFactory;

    myPrevChangeAction = ActionManager.getInstance().getAction("Diff.PrevChange");
    myNextChangeAction = ActionManager.getInstance().getAction("Diff.NextChange");
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

  public boolean quickCheckHaveStuff() {
    if (mySteps.isEmpty()) return false;
    if (mySteps.size() == 1) {
      return mySteps.get(0).haveStuff();
    }
    return true;
  }

  @Nullable
  public DiffRequest init(final int idx) {
    if (idx < 0 || idx > (mySteps.size() - 1)) return null;
    myIndex = idx - 1;
    return moveForward();
  }

  public boolean canMoveForward() {
    return myIndex < (mySteps.size() - 1);
  }

  public boolean canMoveBack() {
    return myIndex > 0;
  }

  @Nullable
  public DiffRequest moveForward() {
    return moveImpl(new MoveDirection() {
      public boolean canMove() {
        return canMoveForward();
      }
      public int direction() {
        return 1;
      }
    });
  }

  @Nullable
  private DiffRequest moveImpl(final MoveDirection moveDirection) {
    while (moveDirection.canMove()) {
      final int nextIdx = myIndex + moveDirection.direction();

      final DiffRequestPresentable diffRequestPresentable = mySteps.get(nextIdx);
      final DiffRequestPresentable.MyResult result = diffRequestPresentable.step(myContext);
      final DiffPresentationReturnValue returnValue = result.getReturnValue();
      if (DiffPresentationReturnValue.quit.equals(returnValue)) {
        return null;
      }
      if (DiffPresentationReturnValue.removeFromList.equals(returnValue)) {
        mySteps.remove(nextIdx);
        if (moveDirection.direction() < 0) {
          // our position moves to head
          myIndex += moveDirection.direction();
        }
        continue;
      }
      final DiffRequest request = result.getRequest();
      takeStuffFromFactory(request, diffRequestPresentable.createActions(myActionsFactory));
      myIndex = nextIdx;
      onEveryMove(request, myShowFrame);
      return request;
    }
    return null;
  }

  private interface MoveDirection {
    boolean canMove();
    int direction();
  }

  @Nullable
  public DiffRequest moveBack() {
    return moveImpl(new MoveDirection() {
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
        public void customize(DiffToolbar toolbar) {
          if (mySteps.size() > 1)
          toolbar.addSeparator();
          toolbar.addAction(myPrevChangeAction);
          toolbar.addAction(myNextChangeAction);

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
