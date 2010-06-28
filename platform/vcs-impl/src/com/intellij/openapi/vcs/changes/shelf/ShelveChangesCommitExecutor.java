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

/*
 * Created by IntelliJ IDEA.
 * User: yole
 * Date: 23.11.2006
 * Time: 13:40:27
 */
package com.intellij.openapi.vcs.changes.shelf;

import com.intellij.CommonBundle;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.changes.*;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Collection;

public class ShelveChangesCommitExecutor implements CommitExecutor {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.vcs.changes.shelf.ShelveChangesCommitExecutor");

  private final Project myProject;

  public ShelveChangesCommitExecutor(final Project project) {
    myProject = project;
  }

  @Nls
  public String getActionText() {
    return VcsBundle.message("shelve.changes.action");
  }

  @NotNull
  public CommitSession createCommitSession() {
    return new ShelveChangesCommitSession();
  }

  private class ShelveChangesCommitSession implements CommitSession {

    @Nullable
    public JComponent getAdditionalConfigurationUI() {
      return null;
    }

    @Nullable
    public JComponent getAdditionalConfigurationUI(final Collection<Change> changes, final String commitMessage) {
      return null;
    }

    public boolean canExecute(Collection<Change> changes, String commitMessage) {
      return changes.size() > 0;
    }

    public void execute(Collection<Change> changes, String commitMessage) {
      if (changes.size() > 0 && !ChangesUtil.hasFileChanges(changes)) {
        ApplicationManager.getApplication().invokeLater(new Runnable() {
          public void run() {
            Messages.showErrorDialog(myProject, VcsBundle.message("shelve.changes.only.directories"), VcsBundle.message("shelve.changes.action"));
          }
        });
        return;
      }
      try {
        final ShelvedChangeList list = ShelveChangesManager.getInstance(myProject).shelveChanges(changes, commitMessage);
        ShelvedChangesViewManager.getInstance(myProject).activateView(list);

        Change[] changesArray = changes.toArray(new Change[changes.size()]);
        // todo better under lock   
        ChangeList changeList = ChangesUtil.getChangeListIfOnlyOne(myProject, changesArray);
        if (changeList instanceof LocalChangeList) {
          LocalChangeList localChangeList = (LocalChangeList) changeList;
          if (localChangeList.getChanges().size() == changes.size() && !localChangeList.isReadOnly()) {
            ChangeListManager.getInstance(myProject).removeChangeList(localChangeList.getName());
          }
        }
      }
      catch (final Exception ex) {
        LOG.info(ex);
        ApplicationManager.getApplication().invokeLater(new Runnable() {
          public void run() {
            Messages.showErrorDialog(myProject, VcsBundle.message("create.patch.error.title", ex.getMessage()), CommonBundle.getErrorTitle());
          }
        }, ModalityState.NON_MODAL);
      }
    }

    public void executionCanceled() {
    }
  }
}
