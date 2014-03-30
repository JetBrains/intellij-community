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

import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ui.ChangesBrowser;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.ui.UIUtil;
import git4idea.GitPlatformFacade;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static com.intellij.openapi.util.text.StringUtil.capitalize;

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
  private final boolean myForceButton;

  /**
   * Shows the dialog with the list of local changes preventing merge/checkout and returns the dialog exit code.
   */
  static int showAndGetAnswer(@NotNull final Project project, @NotNull final List<Change> changes, @NotNull final String operationTitle,
                              final boolean forceButton) {
    final AtomicInteger exitCode = new AtomicInteger();
    UIUtil.invokeAndWaitIfNeeded(new Runnable() {
      @Override
      public void run() {
        GitSmartOperationDialog dialog = new GitSmartOperationDialog(project, changes, operationTitle, forceButton);
        ServiceManager.getService(project, GitPlatformFacade.class).showDialog(dialog);
        exitCode.set(dialog.getExitCode());
      }
    });
    return exitCode.get();
  }

  private GitSmartOperationDialog(@NotNull Project project, @NotNull List<Change> changes, @NotNull String operationTitle,
                                  boolean forceButton) {
    super(project);
    myProject = project;
    myChanges = changes;
    myOperationTitle = operationTitle;
    myForceButton = forceButton;
    setOKButtonText("Smart " + capitalize(myOperationTitle));
    setCancelButtonText("Don't " + capitalize(myOperationTitle));
    getCancelAction().putValue(FOCUSED_ACTION, Boolean.TRUE);
    init();
  }

  @NotNull
  @Override
  protected Action[] createLeftSideActions() {
    if (myForceButton) {
      return new Action[] {new ForceCheckoutAction(myOperationTitle) };
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
    ChangesBrowser changesBrowser =
      new ChangesBrowser(myProject, null, myChanges, null, false, true, null, ChangesBrowser.MyUseCase.LOCAL_CHANGES, null);
    changesBrowser.setChangesToDisplay(myChanges);
    return changesBrowser;
  }

  @Override
  protected String getDimensionServiceKey() {
    return GitSmartOperationDialog.class.getName();
  }


  private class ForceCheckoutAction extends AbstractAction {
    
    ForceCheckoutAction(@NotNull String operationTitle) {
      super("&Force " + capitalize(operationTitle));
    }
    
    @Override
    public void actionPerformed(ActionEvent e) {
      close(FORCE_EXIT_CODE);
    }
  }

}
