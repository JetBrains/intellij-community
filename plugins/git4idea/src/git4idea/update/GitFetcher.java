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
package git4idea.update;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.VirtualFile;
import git4idea.commands.*;

import java.util.Collection;
import java.util.HashSet;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author Kirill Likhodedov
 */
public class GitFetcher {

  private static final Logger LOG = Logger.getInstance(GitFetcher.class);
  private final Project myProject;
  private final ProgressIndicator myProgressIndicator;
  private final Collection<VcsException> myErrors = new HashSet<VcsException>();

  public GitFetcher(Project project, ProgressIndicator progressIndicator) {
    myProject = project;
    myProgressIndicator = progressIndicator;
  }

  /**
   * Invokes 'git fetch'.
   * @param notify specify true to notify about errors.
   * @return true if fetch was successful, false in the case of error.
   */
  public boolean fetch(VirtualFile root) {
    final GitLineHandler h = new GitLineHandler(myProject, root, GitCommand.FETCH);

    final GitTask fetchTask = new GitTask(myProject, h, "Fetching changes...");
    fetchTask.setProgressIndicator(myProgressIndicator);
    fetchTask.setProgressAnalyzer(new GitStandardProgressAnalyzer());
    final AtomicBoolean success = new AtomicBoolean();
    fetchTask.executeInBackground(true, new GitTaskResultHandlerAdapter() {
      @Override protected void onSuccess() {
        success.set(true);
      }

      @Override protected void onCancel() {
        LOG.info("Cancelled fetch.");
      }

      @Override protected void onFailure() {
        LOG.info("Error fetching: " + h.errors());
        myErrors.addAll(h.errors());
      }
    });
    return success.get();
  }

  /**
   * @return true if last {@link #fetch(com.intellij.openapi.vfs.VirtualFile) fetch} performed by this GitFetcher was successful.
   */
  public boolean isSuccess() {
    return myErrors.isEmpty();
  }

  public Collection<VcsException> getErrors() {
    return myErrors;
  }

}
