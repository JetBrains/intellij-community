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
package git4idea.checkout;

import com.intellij.dvcs.DvcsRememberedInputs;
import com.intellij.dvcs.ui.CloneDvcsDialog;
import com.intellij.dvcs.ui.DvcsBundle;
import com.intellij.openapi.project.Project;
import git4idea.GitUtil;
import git4idea.commands.GitCommand;
import git4idea.commands.GitLineHandlerPasswordRequestAware;
import git4idea.commands.GitTask;
import git4idea.commands.GitTaskResult;
import git4idea.remote.GitRememberedInputs;
import org.jetbrains.annotations.NotNull;

import java.io.File;

/**
 * @author Nadya Zabrodina
 */
public class GitCloneDialog extends CloneDvcsDialog {

  public GitCloneDialog(@NotNull Project project) {
    super(project, GitUtil.DOT_GIT);
  }

  /*
   * JGit doesn't have ls-remote command independent from repository yet.
   * That way, we have a hack here: if http response asked for a password, then the url is at least valid and existant, and we consider
   * that the test passed.
   */
  protected boolean test(@NotNull String url) {
    final GitLineHandlerPasswordRequestAware handler =
      new GitLineHandlerPasswordRequestAware(myProject, new File("."), GitCommand.LS_REMOTE);
    handler.setUrl(url);
    handler.addParameters(url, "master");
    GitTask task = new GitTask(myProject, handler, DvcsBundle.message("clone.testing", url));
    GitTaskResult result = task.executeModal();
    boolean authFailed = handler.hadAuthRequest();
    return result.isOK() || authFailed;
  }

  @NotNull
  @Override
  protected DvcsRememberedInputs getRememberedInputs() {
    return GitRememberedInputs.getInstance();
  }

  @Override
  protected String getDimensionServiceKey() {
    return "GitCloneDialog";
  }

  @Override
  protected String getHelpId() {
    return "reference.VersionControl.Git.CloneRepository";
  }
}