/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package git4idea.log;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.PerformInBackgroundOption;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.vcs.VcsDataKeys;
import com.intellij.openapi.vcs.VcsKey;
import com.intellij.openapi.vcs.changes.ui.ChangesViewContentManager;
import com.intellij.openapi.vcs.history.VcsFileRevision;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.openapi.vcs.ui.VcsBalloonProblemNotifier;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentManager;
import com.intellij.vcs.log.impl.VcsLogContentProvider;
import com.intellij.vcs.log.impl.VcsProjectLog;
import com.intellij.vcs.log.ui.VcsLogUiImpl;
import git4idea.GitVcs;
import git4idea.i18n.GitBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

public class GitShowCommitInLogAction extends DumbAwareAction {
  private static final Logger LOG = Logger.getInstance(GitShowCommitInLogAction.class);

  public GitShowCommitInLogAction() {
    super(GitBundle.getString("vcs.history.action.gitlog"));
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent event) {
    final Project project = event.getRequiredData(CommonDataKeys.PROJECT);
    final VcsRevisionNumber revision = getRevisionNumber(event);
    if (revision == null) {
      return;
    }

    VcsProjectLog projectLog = VcsProjectLog.getInstance(project);
    boolean logReady = projectLog.getMainLogUi() != null;
    
    ToolWindow window = ToolWindowManager.getInstance(project).getToolWindow(ChangesViewContentManager.TOOLWINDOW_ID);
    ContentManager cm = window.getContentManager();
    Content[] contents = cm.getContents();
    for (Content content : contents) {
      if (VcsLogContentProvider.TAB_NAME.equals(content.getDisplayName())) {
        cm.setSelectedContent(content);
        break;
      }
    }

    VcsLogUiImpl logUi = projectLog.getMainLogUi();
    if (logUi == null) {
      showLogNotReadyMessage(project);
      return;
    }

    Runnable selectAndOpenLog = () -> {
      Runnable selectCommit = () -> jumpToRevisionUnderProgress(project, logUi, revision);

      if (!window.isVisible()) {
        window.activate(selectCommit, true);
      }
      else {
        selectCommit.run();
      }
    };

    if (logReady) {
      selectAndOpenLog.run();
      return;
    }

    logUi.invokeOnChange(selectAndOpenLog);
  }

  private static void showLogNotReadyMessage(@NotNull Project project) {
    VcsBalloonProblemNotifier.showOverChangesView(project, GitBundle.getString("vcs.history.action.gitlog.error"), MessageType.WARNING);
  }

  @Nullable
  protected VcsRevisionNumber getRevisionNumber(@NotNull AnActionEvent event) {
    VcsRevisionNumber revision = event.getData(VcsDataKeys.VCS_REVISION_NUMBER);
    if (revision == null) {
      VcsFileRevision fileRevision = event.getData(VcsDataKeys.VCS_FILE_REVISION);
      if (fileRevision != null) {
        revision = fileRevision.getRevisionNumber();
      }
    }
    return revision;
  }

  @Nullable
  protected VcsKey getVcsKey(@NotNull AnActionEvent event) {
    return event.getData(VcsDataKeys.VCS);
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    super.update(e);
    Project project = e.getProject();
    e.getPresentation().setEnabled(project != null &&
                                   VcsProjectLog.getInstance(project) != null &&
                                   getRevisionNumber(e) != null &&
                                   Comparing.equal(getVcsKey(e), GitVcs.getKey()));
  }

  private static void jumpToRevisionUnderProgress(@NotNull Project project,
                                                  @NotNull VcsLogUiImpl logUi,
                                                  @NotNull VcsRevisionNumber revision) {
    Future<Boolean> future = logUi.getVcsLog().jumpToReference(revision.asString());
    if (!future.isDone()) {
      ProgressManager.getInstance().run(new Task.Backgroundable(project, "Searching for revision " + revision.asString(),
                                                                false/*can not cancel*/,
                                                                PerformInBackgroundOption.ALWAYS_BACKGROUND) {
        @Override
        public void run(@NotNull ProgressIndicator indicator) {
          try {
            future.get();
            ApplicationManager.getApplication().invokeLater(() -> ensureHasFocus(project, logUi));
          }
          catch (CancellationException | InterruptedException ignored) {
          }
          catch (ExecutionException e) {
            LOG.error(e);
          }
        }
      });
    }
    else {
      ensureHasFocus(project, logUi);
    }
  }

  private static void ensureHasFocus(@Nullable Project project, @NotNull VcsLogUiImpl logUi) {
    if (!logUi.getTable().hasFocus()) {
      IdeFocusManager.getInstance(project).requestFocus(logUi.getTable(), true);
    }
  }
}
