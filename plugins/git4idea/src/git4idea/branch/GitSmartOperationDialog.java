// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.branch;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.ui.JBUI;
import git4idea.DialogManager;
import git4idea.config.GitSaveChangesPolicy;
import git4idea.config.GitVcsSettings;
import git4idea.i18n.GitBundle;
import git4idea.ui.ChangesBrowserWithRollback;
import git4idea.util.GitSimplePathsBrowser;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.util.Collection;
import java.util.List;

import static com.intellij.openapi.util.text.StringUtil.capitalize;

/**
 * The dialog that is shown when the error
 * "Your local changes to the following files would be overwritten by merge/checkout"
 * happens.
 * Displays the list of these files and proposes to make a "smart" merge or checkout.
 */
public final class GitSmartOperationDialog extends DialogWrapper {

  public enum Choice {
    SMART,
    FORCE,
    CANCEL;

    @NotNull
    private static Choice fromDialogExitCode(int exitCode) {
      if (exitCode == OK_EXIT_CODE) return SMART;
      if (exitCode == FORCE_EXIT_CODE) return FORCE;
      if (exitCode == CANCEL_EXIT_CODE) return CANCEL;
      LOG.error("Unexpected exit code: " + exitCode);
      return CANCEL;
    }
  }

  private static final Logger LOG = Logger.getInstance(GitSmartOperationDialog.class);
  private static final int FORCE_EXIT_CODE = NEXT_USER_EXIT_CODE;

  @NotNull private final JComponent myFileBrowser;
  @NotNull private final String myOperationTitle;
  @NotNull private final GitSaveChangesPolicy mySaveMethod;
  @Nullable private final String myForceButton;

  /**
   * Shows the dialog with the list of local changes preventing merge/checkout and returns the user's choice.
   */
  @NotNull
  static Choice show(@NotNull Project project,
                     @NotNull List<? extends Change> changes,
                     @NotNull Collection<String> paths,
                     @NotNull @Nls(capitalization = Nls.Capitalization.Title) String operationTitle,
                     @Nullable @Nls(capitalization = Nls.Capitalization.Title) String forceButtonTitle) {
    JComponent fileBrowser = !changes.isEmpty()
                             ? new ChangesBrowserWithRollback(project, changes)
                             : new GitSimplePathsBrowser(project, paths);
    GitSmartOperationDialog dialog = new GitSmartOperationDialog(project, fileBrowser, operationTitle, forceButtonTitle);
    if (fileBrowser instanceof Disposable) Disposer.register(dialog.getDisposable(), (Disposable)fileBrowser);
    DialogManager.show(dialog);
    return Choice.fromDialogExitCode(dialog.getExitCode());
  }

  private GitSmartOperationDialog(@NotNull Project project,
                                  @NotNull JComponent fileBrowser,
                                  @NotNull @Nls(capitalization = Nls.Capitalization.Title) String operationTitle,
                                  @Nullable @Nls(capitalization = Nls.Capitalization.Title) String forceButton) {
    super(project);
    myFileBrowser = fileBrowser;
    myOperationTitle = operationTitle;
    myForceButton = forceButton;
    mySaveMethod = GitVcsSettings.getInstance(project).getSaveChangesPolicy();
    String capitalizedOperation = capitalize(myOperationTitle);
    setTitle(GitBundle.message("smart.operation.dialog.git.operation.name.problem", capitalizedOperation));

    setOKButtonText(GitBundle.message("smart.operation.dialog.smart.operation.name", capitalizedOperation));
    String description = mySaveMethod.selectBundleMessage(
      GitBundle.message("smart.operation.dialog.ok.action.stash.description", operationTitle),
      GitBundle.message("smart.operation.dialog.ok.action.shelf.description", operationTitle)
    );
    getOKAction().putValue(Action.SHORT_DESCRIPTION, description);
    setCancelButtonText(GitBundle.message("smart.operation.dialog.don.t.operation.name", capitalizedOperation));
    getCancelAction().putValue(FOCUSED_ACTION, Boolean.TRUE);
    init();
  }

  @Override
  protected Action @NotNull [] createLeftSideActions() {
    if (myForceButton != null) {
      return new Action[]{new ForceCheckoutAction(myForceButton, myOperationTitle)};
    }
    return new Action[0];
  }

  @Override
  protected JComponent createNorthPanel() {
    String labelText = mySaveMethod.selectBundleMessage(
      GitBundle.message(
        "smart.operation.dialog.north.panel.label.stash.text",
        myOperationTitle,
        ApplicationNamesInfo.getInstance().getFullProductName()
      ),
      GitBundle.message(
        "smart.operation.dialog.north.panel.label.shelf.text",
        myOperationTitle,
        ApplicationNamesInfo.getInstance().getFullProductName()
      )
    );
    return new JBLabel(labelText).withBorder(JBUI.Borders.emptyBottom(10));
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

    ForceCheckoutAction(@NotNull @Nls(capitalization = Nls.Capitalization.Title) String buttonTitle,
                        @NotNull @Nls(capitalization = Nls.Capitalization.Title) String operationTitle) {
      super(buttonTitle);
      String description = GitBundle.message("smart.operation.dialog.operation.name.and.overwrite.local.changes",
                                             capitalize(operationTitle));
      putValue(Action.SHORT_DESCRIPTION, description);
    }

    @Override
    public void actionPerformed(ActionEvent e) {
      close(FORCE_EXIT_CODE);
    }
  }
}
