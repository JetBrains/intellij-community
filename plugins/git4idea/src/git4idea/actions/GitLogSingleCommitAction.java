/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package git4idea.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.vcs.log.VcsFullCommitDetails;
import com.intellij.vcs.log.VcsLog;
import com.intellij.vcs.log.VcsLogDataKeys;
import git4idea.GitUtil;
import git4idea.repo.GitRepository;
import git4idea.repo.GitRepositoryManager;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author Kirill Likhodedov
 */
public abstract class GitLogSingleCommitAction extends DumbAwareAction {

  private static final Logger LOG = Logger.getInstance(GitLogSingleCommitAction.class);

  protected abstract void actionPerformed(@NotNull GitRepository repository, @NotNull VcsFullCommitDetails commit);

  @Override
  public void actionPerformed(AnActionEvent e) {
    Data data = Data.collect(e);
    if (!data.isValid()) {
      return;
    }

    List<VcsFullCommitDetails> details = data.log.getSelectedDetails();
    if (details.size() != 1) {
      return;
    }
    VcsFullCommitDetails commit = details.get(0);

    GitRepositoryManager repositoryManager = ServiceManager.getService(data.project, GitRepositoryManager.class);
    final GitRepository repository = repositoryManager.getRepositoryForRoot(commit.getRoot());
    if (repository == null) {
      GitUtil.noRepositoryForRoot(LOG, commit.getRoot(), data.project);
      return;
    }
    
    actionPerformed(repository, commit);
  }

  @Override
  public void update(AnActionEvent e) {
    Data data = Data.collect(e);
    boolean enabled = data.isValid() && data.log.getSelectedCommits().size() == 1;
    e.getPresentation().setVisible(data.isValid());
    e.getPresentation().setEnabled(enabled);
  }

  private static class Data {
    Project project;
    VcsLog log;

    static Data collect(AnActionEvent e) {
      Data data = new Data();
      data.project = e.getData(CommonDataKeys.PROJECT);
      data.log = e.getData(VcsLogDataKeys.VSC_LOG);
      return data;
    }

    boolean isValid() {
      return project != null && log != null && GitUtil.logHasGitRoot(log);
    }
  }

}
