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
package git4idea.commands;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.HtmlBuilder;
import com.intellij.openapi.util.text.HtmlChunk;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import git4idea.GitUtil;
import git4idea.repo.GitRepository;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Compound result of the Git command execution performed on several repositories.
 *
 * @author Kirill Likhodedov
 */
public final class GitCompoundResult {

  private final MultiMap<GitRepository, GitCommandResult> resultsByRepos = new MultiMap<>();
  private final Project myProject;

  public GitCompoundResult(Project project) {
    myProject = project;
  }

  public void append(GitRepository repository, GitCommandResult result) {
    resultsByRepos.putValue(repository, result);
  }

  public boolean totalSuccess() {
    boolean success = true;
    for (GitCommandResult result : resultsByRepos.values()) {
      success &= result.success();
    }
    return success;
  }

  /**
   * @return true if at least one, but not all repositories succeeded.
   */
  public boolean partialSuccess() {
    boolean successFound = false;
    boolean failureFound = false;
    for (GitCommandResult result : resultsByRepos.values()) {
      if (result.success()) {
        successFound = true;
      } else {
        failureFound = true;
      }
    }
    return successFound && failureFound;
  }

  /**
   * Constructs the HTML-formatted message from error outputs of failed repositories.
   * If there is only 1 repository in the project, just returns the error without writing the repository url (to avoid confusion for people
   * with only 1 root ever).
   * Otherwise adds repository URL to the error that repository produced.
   */
  @NotNull
  @NlsContexts.NotificationContent
  public String getErrorOutputWithReposIndication() {
    HtmlBuilder sb = new HtmlBuilder();
    for (Map.Entry<GitRepository, Collection<GitCommandResult>> entry : resultsByRepos.entrySet()) {
      GitRepository repository = entry.getKey();
      List<GitCommandResult> errors = ContainerUtil.filter(entry.getValue(), it -> !it.success());
      if (!errors.isEmpty()) {
        HtmlBuilder repoError = new HtmlBuilder();
        if (!GitUtil.justOneGitRepository(myProject)) {
          repoError.append(HtmlChunk.text(repository.getPresentableUrl()).code());
          repoError.append(":").br();
        }
        for (int i = 0; i < errors.size(); i++) {
          if (i > 0) repoError.br();
          repoError.appendRaw(errors.get(i).getErrorOutputAsHtmlString());
        }
        sb.append(repoError.wrapWith("p"));
      }
    }
    return sb.toString();
  }

  @NonNls
  @Override
  public String toString() {
    return "GitCompoundResult: " + StringUtil.join(resultsByRepos.keySet(), repository -> repository.getRoot().getName() + ": " + resultsByRepos.get(repository).toString(), "\n");
  }
}
