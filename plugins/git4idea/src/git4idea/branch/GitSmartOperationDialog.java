/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package git4idea.branch;

import com.intellij.openapi.actionSystem.EmptyAction;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vcs.changes.actions.RollbackDialogAction;
import com.intellij.openapi.vcs.changes.ui.ChangesBrowser;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.UIUtil;
import git4idea.GitPlatformFacade;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static com.intellij.openapi.util.text.StringUtil.capitalize;
import static com.intellij.openapi.vcs.changes.ui.ChangesBrowser.MyUseCase.LOCAL_CHANGES;

/**
 * The dialog that is shown when the error
 * "Your local changes to the following files would be overwritten by merge/checkout"
 * happens.
 * Displays the list of these files and proposes to make a "smart" merge or checkout.
 *
 * @author Kirill Likhodedov
 */
public class GitSmartOperationDialog extends DialogWrapper {

  public static final int SMART_EXIT_CODE = OK_EXIT_CODE;
  public static final int FORCE_EXIT_CODE = NEXT_USER_EXIT_CODE;
  
  private final Project myProject;
  private final List<Change> myChanges;
  @NotNull private final String myOperationTitle;
  private final boolean myShowForceButton;

  /**
   * Shows the dialog with the list of local changes preventing merge/checkout and returns the dialog exit code.
   */
  static int showAndGetAnswer(@NotNull final Project project, @NotNull final List<Change> changes, @NotNull final String operationTitle,
                              final boolean showForceButton) {
    final AtomicInteger exitCode = new AtomicInteger();
    UIUtil.invokeAndWaitIfNeeded(new Runnable() {
      @Override
      public void run() {
        GitSmartOperationDialog dialog = new GitSmartOperationDialog(project, changes, operationTitle, showForceButton);
        ServiceManager.getService(project, GitPlatformFacade.class).showDialog(dialog);
        exitCode.set(dialog.getExitCode());
      }
    });
    return exitCode.get();
  }

  private GitSmartOperationDialog(@NotNull Project project, @NotNull List<Change> changes, @NotNull String operationTitle,
                                  boolean showForceButton) {
    super(project);
    myProject = project;
    myChanges = changes;
    myOperationTitle = operationTitle;
    myShowForceButton = showForceButton;
    String capitalizedOperation = capitalize(myOperationTitle);
    setTitle("Git " + capitalizedOperation + " Problem");

    setOKButtonText("Smart " + capitalizedOperation);
    getOKAction().putValue(Action.SHORT_DESCRIPTION, "Stash local changes, " + operationTitle + ", unstash");
    setCancelButtonText("Don't " + capitalizedOperation);
    getCancelAction().putValue(FOCUSED_ACTION, Boolean.TRUE);
    init();
  }

  @NotNull
  @Override
  protected Action[] createLeftSideActions() {
    if (myShowForceButton) {
      return new Action[]  {new ForceCheckoutAction(myOperationTitle) };
    }
    return new Action[0];
  }

  @Override
  protected JComponent createNorthPanel() {
    JBLabel description = new JBLabel("<html>Your local changes to the following files would be overwritten by " + myOperationTitle +
                                      ".<br/>" + ApplicationNamesInfo.getInstance().getFullProductName() + " can stash the changes, "
                                      + myOperationTitle + " and unstash them after that.</html>");
    description.setBorder(IdeBorderFactory.createEmptyBorder(0, 0, 10, 0));
    return description;
  }

  @Override
  protected JComponent createCenterPanel() {
    ChangesBrowser changesBrowser = new ChangesBrowser(myProject, null, myChanges, null, false, true, null, LOCAL_CHANGES, null) {
        @Override
        public void rebuildList() {
          myChangesToDisplay = filterActualChanges();
          super.rebuildList();
        }
      };
    RollbackDialogAction rollback = new RollbackDialogAction();
    EmptyAction.setupAction(rollback, IdeActions.CHANGES_VIEW_ROLLBACK, changesBrowser);
    changesBrowser.addToolbarAction(rollback);
    changesBrowser.setChangesToDisplay(myChanges);
    return changesBrowser;
  }

  @NotNull
  private List<Change> filterActualChanges() {
    final Collection<Change> allChanges = ChangeListManager.getInstance(myProject).getAllChanges();
    return ContainerUtil.filter(myChanges, new Condition<Change>() {
      @Override
      public boolean value(Change change) {
        return allChanges.contains(change);
      }
    });
  }

  @Override
  protected String getDimensionServiceKey() {
    return GitSmartOperationDialog.class.getName();
  }


  private class ForceCheckoutAction extends AbstractAction {
    
    ForceCheckoutAction(@NotNull String operationTitle) {
      super("&Force " + capitalize(operationTitle));
      putValue(Action.SHORT_DESCRIPTION, capitalize(operationTitle) + " and overwrite local changes");
    }
    
    @Override
    public void actionPerformed(ActionEvent e) {
      close(FORCE_EXIT_CODE);
    }
  }

}
