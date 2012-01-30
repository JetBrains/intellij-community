/*
 * Copyright 2000-2011 JetBrains s.r.o.
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

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ui.ChangesBrowser;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.ui.UIUtil;
import git4idea.DialogManager;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The dialog that is shown when the error "The following files would be overwritten by checkout" happens.
 * Displays the list of these files and proposes to make a "smart" checkout.
 *
 * @author Kirill Likhodedov
 */
// TODO "don't ask again" option
class GitWouldBeOverwrittenByCheckoutDialog extends DialogWrapper {

  public static final int SMART_CHECKOUT = OK_EXIT_CODE;
  public static final int FORCE_CHECKOUT_EXIT_CODE = NEXT_USER_EXIT_CODE;
  
  private final Project myProject;
  private final List<Change> myChanges;

  /**
   * @return true if smart checkout has to be performed, false if user doesn't want to checkout.
   */
  static int showAndGetAnswer(@NotNull final Project project, @NotNull final List<Change> changes) {
    final AtomicInteger exitCode = new AtomicInteger();
    UIUtil.invokeAndWaitIfNeeded(new Runnable() {
      @Override
      public void run() {
        GitWouldBeOverwrittenByCheckoutDialog dialog = new GitWouldBeOverwrittenByCheckoutDialog(project, changes);
        DialogManager.getInstance(project).showDialog(dialog);
        exitCode.set(dialog.getExitCode());
      }
    });
    return exitCode.get();
  }

  private GitWouldBeOverwrittenByCheckoutDialog(@NotNull Project project, @NotNull List<Change> changes) {
    super(project);
    myProject = project;
    myChanges = changes;
    setOKButtonText("Smart checkout");
    setCancelButtonText("Don't checkout");
    getCancelAction().putValue(FOCUSED_ACTION, Boolean.TRUE);
    init();
  }

  @Override
  protected Action[] createLeftSideActions() {
    return new Action[] {new ForceCheckoutAction() };
  }

  @Override
  protected JComponent createNorthPanel() {
    JBLabel description = new JBLabel("<html>Your local changes to the following files would be overwritten by checkout.<br/>" +
                                      "IDEA can stash the changes, checkout and unstash them after that.</html>");
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
    return GitWouldBeOverwrittenByCheckoutDialog.class.getName();
  }


  private class ForceCheckoutAction extends AbstractAction {
    
    ForceCheckoutAction() {
      super("Force checkout");
    }
    
    @Override
    public void actionPerformed(ActionEvent e) {
      close(FORCE_CHECKOUT_EXIT_CODE);
    }
  }

}
