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
package org.jetbrains.plugins.github;

import com.intellij.ide.BrowserUtil;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import git4idea.GitUtil;
import git4idea.repo.GitRepository;

/**
 * @author Kirill Likhodedov
 */
abstract class GithubShowCommitInBrowserAction extends DumbAwareAction {

  public GithubShowCommitInBrowserAction() {
    super("Open in Browser", "Open the selected commit in browser", GithubUtil.GITHUB_ICON);
  }

  protected static void openInBrowser(Project project, GitRepository repository, String revisionHash) {
    String url = GithubUtil.findGithubRemoteUrl(repository);
    if (url == null) {
      GithubUtil.LOG.info(String.format("Repository is not under GitHub. Root: %s, Remotes: %s", repository.getRoot(),
                                        GitUtil.getPrintableRemotes(repository.getRemotes())));
      return;
    }
    url = GithubUtil.makeGithubRepoUrlFromRemoteUrl(url);
    String userAndRepository = GithubUtil.getUserAndRepositoryOrShowError(project, url);
    if (userAndRepository == null) {
      return;
    }

    String githubUrl = "https://github.com/" + userAndRepository + "/commit/" + revisionHash;
    BrowserUtil.launchBrowser(githubUrl);
  }

}
