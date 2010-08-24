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
package com.intellij.openapi.vcs.history;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.BackgroundFromStartOption;
import com.intellij.openapi.vcs.impl.BackgroundableActionEnabledHandler;
import com.intellij.openapi.vcs.impl.ProjectLevelVcsManagerImpl;
import com.intellij.openapi.vcs.impl.VcsBackgroundableActions;
import com.intellij.openapi.vcs.impl.VcsBackgroundableComputable;
import com.intellij.util.Consumer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class VcsHistoryProviderBackgroundableProxy {
  private final Project myProject;
  private final VcsHistoryProvider myDelegate;

  public VcsHistoryProviderBackgroundableProxy(final Project project, final VcsHistoryProvider delegate) {
    myDelegate = delegate;
    myProject = project;
  }

  public void createSessionFor(final FilePath filePath, final Consumer<VcsHistorySession> continuation,
                               @Nullable VcsBackgroundableActions actionKey,
                               final boolean silent,
                               @Nullable final Consumer<VcsHistorySession> backgroundSpecialization) {
    final ThrowableComputable<VcsHistorySession, VcsException> throwableComputable =
      new ThrowableComputable<VcsHistorySession, VcsException>() {
        public VcsHistorySession compute() throws VcsException {
          final VcsHistorySession sessionFor = myDelegate.createSessionFor(filePath);
          if (backgroundSpecialization != null) {
            backgroundSpecialization.consume(sessionFor);
          }
          return sessionFor;
        }
      };
    final VcsBackgroundableActions resultingActionKey = actionKey == null ? VcsBackgroundableActions.CREATE_HISTORY_SESSION : actionKey;
    final Object key = VcsBackgroundableActions.keyFrom(filePath);

    if (silent) {
      VcsBackgroundableComputable.createAndRunSilent(myProject, resultingActionKey, key, VcsBundle.message("loading.file.history.progress"),
                                                     throwableComputable, continuation);
    } else {
      VcsBackgroundableComputable.createAndRun(myProject, resultingActionKey, key, VcsBundle.message("loading.file.history.progress"),
      VcsBundle.message("message.title.could.not.load.file.history"), throwableComputable, continuation, null);
    }
  }

  public void executeAppendableSession(final FilePath filePath, final VcsAppendableHistorySessionPartner partner, 
                                       @Nullable VcsBackgroundableActions actionKey, final boolean silent) {
    final ProjectLevelVcsManagerImpl vcsManager = (ProjectLevelVcsManagerImpl) ProjectLevelVcsManager.getInstance(myProject);
    final VcsBackgroundableActions resultingActionKey = actionKey == null ? VcsBackgroundableActions.CREATE_HISTORY_SESSION : actionKey;

    final BackgroundableActionEnabledHandler handler;
    handler = vcsManager.getBackgroundableActionHandler(resultingActionKey);
    // fo not start same action twice
    if (handler.isInProgress(resultingActionKey)) return;

    handler.register(resultingActionKey);

    ProgressManager.getInstance().run(new Task.Backgroundable(myProject, VcsBundle.message("loading.file.history.progress"),
                                                              true, BackgroundFromStartOption.getInstance()) {
      public void run(@NotNull ProgressIndicator indicator) {
        try {
          myDelegate.reportAppendableHistory(filePath, partner);
        }
        catch (VcsException e) {
          partner.reportException(e);
        }
        finally {
          partner.finished();
          ApplicationManager.getApplication().invokeLater(new Runnable() {
            public void run() {
              handler.completed(resultingActionKey);
            }
          }, ModalityState.NON_MODAL);
        }
      }
    });
  }
}
