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
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.PerformInBackgroundOption;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.vcs.VcsDataKeys;
import com.intellij.openapi.vcs.VcsKey;
import com.intellij.openapi.vcs.history.VcsFileRevision;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.vcs.log.Hash;
import com.intellij.vcs.log.impl.HashImpl;
import com.intellij.vcs.log.impl.VcsLogContentUtil;
import com.intellij.vcs.log.impl.VcsProjectLog;
import com.intellij.vcs.log.ui.VcsLogUiEx;
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
    super(GitBundle.messagePointer("vcs.history.action.gitlog"));
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent event) {
    final Project project = event.getRequiredData(CommonDataKeys.PROJECT);
    final VcsRevisionNumber revision = getRevisionNumber(event);
    if (revision == null) {
      return;
    }

    jumpToRevision(project, HashImpl.build(revision.asString()));
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
    Project project = e.getProject();
    e.getPresentation().setEnabled(project != null &&
                                   VcsProjectLog.getInstance(project) != null &&
                                   getRevisionNumber(e) != null &&
                                   Comparing.equal(getVcsKey(e), GitVcs.getKey()));
  }

  static void jumpToRevision(@NotNull Project project, @NotNull Hash hash) {
    VcsLogContentUtil.runInMainLog(project, logUi -> jumpToRevisionUnderProgress(project, logUi, hash));
  }

  private static void jumpToRevisionUnderProgress(@NotNull Project project,
                                                  @NotNull VcsLogUiEx logUi,
                                                  @NotNull Hash hash) {
    Future<Boolean> future = logUi.getVcsLog().jumpToReference(hash.asString());
    if (!future.isDone()) {
      ProgressManager.getInstance().run(new Task.Backgroundable(project,
                                                                GitBundle.message("git.log.show.commit.in.log.process", hash.asString()),
                                                                false/*can not cancel*/,
                                                                PerformInBackgroundOption.ALWAYS_BACKGROUND) {
        @Override
        public void run(@NotNull ProgressIndicator indicator) {
          try {
            future.get();
          }
          catch (CancellationException | InterruptedException ignored) {
          }
          catch (ExecutionException e) {
            LOG.error(e);
          }
        }
      });
    }
  }
}
