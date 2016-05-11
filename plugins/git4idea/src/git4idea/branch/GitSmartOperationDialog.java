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

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.components.JBLabel;
import git4idea.DialogManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.util.concurrent.atomic.AtomicInteger;

import static com.intellij.openapi.util.text.StringUtil.capitalize;

/**
 * The dialog that is shown when the error
 * "Your local changes to the following files would be overwritten by merge/checkout"
 * happens.
 * Displays the list of these files and proposes to make a "smart" merge or checkout.
 */
public class GitSmartOperationDialog extends DialogWrapper {

  public static final int SMART_EXIT_CODE = OK_EXIT_CODE;
  public static final int FORCE_EXIT_CODE = NEXT_USER_EXIT_CODE;

  @NotNull private final JComponent myFileBrowser;
  @NotNull private final String myOperationTitle;
  @Nullable private final String myForceButton;

  /**
   * Shows the dialog with the list of local changes preventing merge/checkout and returns the dialog exit code.
   */
  static int showAndGetAnswer(@NotNull final Project project, @NotNull final JComponent fileBrowser,
                              @NotNull final String operationTitle, @Nullable final String forceButtonTitle) {
    final AtomicInteger exitCode = new AtomicInteger();
    ApplicationManager.getApplication().invokeAndWait(() -> {
      GitSmartOperationDialog dialog = new GitSmartOperationDialog(project, fileBrowser, operationTitle, forceButtonTitle);
      DialogManager.show(dialog);
      exitCode.set(dialog.getExitCode());
    }, ModalityState.defaultModalityState());
    return exitCode.get();
  }

  private GitSmartOperationDialog(@NotNull Project project, @NotNull JComponent fileBrowser, @NotNull String operationTitle,
                                  @Nullable String forceButton) {
    super(project);
    myFileBrowser = fileBrowser;
    myOperationTitle = operationTitle;
    myForceButton = forceButton;
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
    if (myForceButton != null) {
      return new Action[]{new ForceCheckoutAction(myForceButton, myOperationTitle)};
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
    return myFileBrowser;
  }

  @Override
  protected String getDimensionServiceKey() {
    return GitSmartOperationDialog.class.getName();
  }


  private class ForceCheckoutAction extends AbstractAction {
    
    ForceCheckoutAction(@NotNull String buttonTitle, @NotNull String operationTitle) {
      super(buttonTitle);
      putValue(Action.SHORT_DESCRIPTION, capitalize(operationTitle) + " and overwrite local changes");
    }
    
    @Override
    public void actionPerformed(ActionEvent e) {
      close(FORCE_EXIT_CODE);
    }
  }

}
