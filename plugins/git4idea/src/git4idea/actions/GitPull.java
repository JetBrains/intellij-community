/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.vfs.VirtualFile;
import git4idea.GitUtil;
import git4idea.commands.GitLineHandler;
import git4idea.i18n.GitBundle;
import git4idea.merge.GitPullDialog;
import git4idea.repo.GitRemote;
import git4idea.repo.GitRepository;
import git4idea.repo.GitRepositoryManager;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

public class GitPull extends GitMergeAction {

  @Override
  @NotNull
  protected String getActionName() {
    return GitBundle.getString("pull.action.name");
  }

  @Override
  protected DialogState displayDialog(@NotNull Project project, @NotNull List<VirtualFile> gitRoots,
                                                                        @NotNull VirtualFile defaultRoot) {
    final GitPullDialog dialog = new GitPullDialog(project, gitRoots, defaultRoot);
    if (!dialog.showAndGet()) {
      return null;
    }

    GitRepositoryManager repositoryManager = GitUtil.getRepositoryManager(project);
    GitRepository repository = repositoryManager.getRepositoryForRoot(dialog.gitRoot());
    assert repository != null : "Repository can't be null for root " + dialog.gitRoot();
    String remoteOrUrl = dialog.getRemote();
    if (remoteOrUrl == null) {
      return null;
    }

    GitRemote remote = GitUtil.findRemoteByName(repository, remoteOrUrl);
    final List<String> urls = remote == null ? Collections.singletonList(remoteOrUrl) : remote.getUrls();
    Computable<GitLineHandler> handlerProvider = new Computable<GitLineHandler>() {
      @Override
      public GitLineHandler compute() {
        return dialog.makeHandler(urls);
      }
    };
    return new DialogState(dialog.gitRoot(), GitBundle.message("pulling.title", dialog.getRemote()), handlerProvider);
  }

}
