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
package git4idea.util;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Clock;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.openapi.vcs.merge.MergeDialogCustomizer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.text.DateFormatUtil;
import git4idea.GitUtil;
import git4idea.NotificationManager;
import git4idea.merge.GitConflictResolver;
import git4idea.repo.GitRepository;
import git4idea.stash.GitStashChangesSaver;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

import static com.intellij.openapi.util.text.StringUtil.join;

/**
 * Executes a Git operation on a number of repositories surrounding it by stash-unstash procedure.
 * I.e. stashes changes, executes the operation and then unstashes it.
 *
 * @author Kirill Likhodedov
 */
public class GitPreservingProcess {

  private static final Logger LOG = Logger.getInstance(GitPreservingProcess.class);

  @NotNull private final Project myProject;
  @NotNull private final Collection<GitRepository> myRepositories;
  @NotNull private final String myOperationTitle;
  @NotNull private final String myDestinationName;
  @NotNull private final ProgressIndicator myProgressIndicator;
  @NotNull private final Runnable myOperation;
  @NotNull private final String myStashMessage;

  private GitStashChangesSaver mySaver;
  private boolean myLoaded;
  private final Object LOAD_LOCK = new Object();

  public GitPreservingProcess(@NotNull Project project, @NotNull Collection<GitRepository> repositories,
                              @NotNull String operationTitle, @NotNull String destinationName,
                              @NotNull ProgressIndicator indicator, @NotNull Runnable operation) {
    myProject = project;
    myRepositories = repositories;
    myOperationTitle = operationTitle;
    myDestinationName = destinationName;
    myProgressIndicator = indicator;
    myOperation = operation;
    myStashMessage = String.format("%s %s at %s", StringUtil.capitalize(myOperationTitle), myDestinationName,
                                   DateFormatUtil.formatDateTime(Clock.getTime()));
  }

  public void execute() {
    execute(null);
  }

  public void execute(@Nullable final Computable<Boolean> autoLoadDecision) {
    Runnable operation = new Runnable() {
      @Override
      public void run() {
        mySaver = configureSaver();
        boolean savedSuccessfully = save();
        if (savedSuccessfully) {
          try {
            myOperation.run();
          }
          finally {
            if (autoLoadDecision == null || autoLoadDecision.compute()) {
              load();
            }
          }
        }
      }
    };

    new GitFreezingProcess(myProject, myOperationTitle, operation).execute();
  }

  /**
   * Configures the saver, actually notifications and texts in the GitConflictResolver used inside.
   */
  private GitStashChangesSaver configureSaver() {
    GitStashChangesSaver saver = new GitStashChangesSaver(myProject, myProgressIndicator, myStashMessage);
    MergeDialogCustomizer mergeDialogCustomizer = new MergeDialogCustomizer() {
      @Override
      public String getMultipleFileMergeDescription(Collection<VirtualFile> files) {
        return String.format(
          "<html>Uncommitted changes that were saved before %s have conflicts with files from <code>%s</code></html>",
          myOperationTitle, myDestinationName);
      }

      @Override
      public String getLeftPanelTitle(VirtualFile file) {
        return "Uncommitted changes from stash";
      }

      @Override
      public String getRightPanelTitle(VirtualFile file, VcsRevisionNumber lastRevisionNumber) {
        return String.format("<html>Changes from <b><code>%s</code></b></html>", myDestinationName);
      }
    };

    GitConflictResolver.Params params = new GitConflictResolver.Params().
      setReverse(true).
      setMergeDialogCustomizer(mergeDialogCustomizer).
      setErrorNotificationTitle("Local changes were not restored");

    saver.setConflictResolverParams(params);
    return saver;
  }

  /**
   * Saves local changes. In case of error shows a notification and returns false.
   */
  private boolean save() {
    try {
      mySaver.saveLocalChanges(GitUtil.getRoots(myRepositories));
      return true;
    } catch (VcsException e) {
      LOG.info("Couldn't save local changes", e);
      NotificationManager.getInstance(myProject).notifyError(
        "Couldn't save uncommitted changes.",
        String.format("Tried to save uncommitted changes in stash before %s, but failed with an error.<br/>%s",
                      myOperationTitle, join(e.getMessages())));
      return false;
    }
  }

  public void load() {
    synchronized (LOAD_LOCK) {
      if (myLoaded) {
        return;
      }
      try {
        mySaver.load();
        myLoaded = true;
      }
      catch (VcsException e) {
        LOG.info("Couldn't load local changes", e);
        NotificationManager.getInstance(myProject).notifyError("Couldn't restore uncommitted changes",
          String.format("Tried to unstash uncommitted changes, but failed with error.<br/>%s",join(e.getMessages())));
      }
    }
  }

}
