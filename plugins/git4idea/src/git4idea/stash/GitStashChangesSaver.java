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
package git4idea.stash;

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationListener;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.VcsNotifier;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.openapi.vcs.merge.MergeDialogCustomizer;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import git4idea.GitUtil;
import git4idea.commands.Git;
import git4idea.commands.GitCommandResult;
import git4idea.commands.GitHandlerUtil;
import git4idea.commands.GitSimpleEventDetector;
import git4idea.merge.GitConflictResolver;
import git4idea.repo.GitRepository;
import git4idea.repo.GitRepositoryManager;
import git4idea.ui.GitUnstashDialog;
import git4idea.util.GitUIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.event.HyperlinkEvent;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Set;

public class GitStashChangesSaver extends GitChangesSaver {

  private static final Logger LOG = Logger.getInstance(GitStashChangesSaver.class);
  private static final String NO_LOCAL_CHANGES_TO_SAVE = "No local changes to save";

  @NotNull private final GitRepositoryManager myRepositoryManager;
  @NotNull private final Set<VirtualFile> myStashedRoots = ContainerUtil.newHashSet(); // save stashed roots to unstash only them

  public GitStashChangesSaver(@NotNull Project project,
                              @NotNull Git git,
                              @NotNull ProgressIndicator progressIndicator,
                              @NotNull String stashMessage) {
    super(project, git, progressIndicator, stashMessage);
    myRepositoryManager = GitUtil.getRepositoryManager(project);
  }

  @Override
  protected void save(@NotNull Collection<VirtualFile> rootsToSave) throws VcsException {
    LOG.info("saving " + rootsToSave);

    for (VirtualFile root : rootsToSave) {
      final String message = GitHandlerUtil.formatOperationName("Stashing changes from", root);
      LOG.info(message);
      final String oldProgressTitle = myProgressIndicator.getText();
      myProgressIndicator.setText(message);
      GitRepository repository = myRepositoryManager.getRepositoryForRoot(root);
      if (repository == null) {
        LOG.error("Repository is null for root " + root);
      }
      else {
        GitCommandResult result = myGit.stashSave(repository, myStashMessage);
        if (result.success() && somethingWasStashed(result)) {
          myStashedRoots.add(root);
        }
        else {
          String error = "stash " + repository.getRoot() + ": " + result.getErrorOutputAsJoinedString();
          if (!result.success()) {
            throw new VcsException(error);
          }
          else {
            LOG.warn(error);
          }
        }
      }
      myProgressIndicator.setText(oldProgressTitle);
    }
  }

  private static boolean somethingWasStashed(@NotNull GitCommandResult result) {
    return !StringUtil.containsIgnoreCase(result.getErrorOutputAsJoinedString(), NO_LOCAL_CHANGES_TO_SAVE) &&
           !StringUtil.containsIgnoreCase(result.getOutputAsJoinedString(), NO_LOCAL_CHANGES_TO_SAVE);
  }

  @Override
  public void load() {
    for (VirtualFile root : myStashedRoots) {
      loadRoot(root);
    }

    boolean conflictsResolved = new UnstashConflictResolver(myProject, myGit, myStashedRoots, myParams).merge();
    LOG.info("load: conflicts resolved status is " + conflictsResolved + " in roots " + myStashedRoots);
  }

  @Override
  public boolean wereChangesSaved() {
    return !myStashedRoots.isEmpty();
  }

  @Override
  public String getSaverName() {
    return "stash";
  }

  @NotNull
  @Override
  public String getOperationName() {
    return "stash";
  }

  @Override
  public void showSavedChanges() {
    GitUnstashDialog.showUnstashDialog(myProject, new ArrayList<VirtualFile>(myStashedRoots), myStashedRoots.iterator().next());
  }

  /**
   * Returns true if the root was loaded with conflict.
   * False is returned in all other cases: in the case of success and in case of some other error.
   */
  private boolean loadRoot(final VirtualFile root) {
    LOG.info("loadRoot " + root);
    myProgressIndicator.setText(GitHandlerUtil.formatOperationName("Unstashing changes to", root));

    GitRepository repository = myRepositoryManager.getRepositoryForRoot(root);
    if (repository == null) {
      LOG.error("Repository is null for root " + root);
      return false;
    }

    GitSimpleEventDetector conflictDetector = new GitSimpleEventDetector(GitSimpleEventDetector.Event.MERGE_CONFLICT_ON_UNSTASH);
    GitCommandResult result = myGit.stashPop(repository, conflictDetector);
    VfsUtil.markDirtyAndRefresh(false, true, false, root);
    if (result.success()) {
      return false;
    }
    else if (conflictDetector.hasHappened()) {
      return true;
    }
    else {
      LOG.info("unstash failed " + result.getErrorOutputAsJoinedString());
      GitUIUtil.notifyImportantError(myProject, "Couldn't unstash", "<br/>" + result.getErrorOutputAsHtmlString());
      return false;
    }
  }

  @Override
  public String toString() {
    return "StashChangesSaver. Roots: " + myStashedRoots;
  }

  private static class UnstashConflictResolver extends GitConflictResolver {

    private final Set<VirtualFile> myStashedRoots;

    public UnstashConflictResolver(@NotNull Project project, @NotNull Git git,
                                   @NotNull Set<VirtualFile> stashedRoots, @Nullable Params params) {
      super(project, git, stashedRoots, makeParamsOrUse(params));
      myStashedRoots = stashedRoots;
    }

    private static Params makeParamsOrUse(@Nullable Params givenParams) {
      if (givenParams != null) {
        return givenParams;
      }
      Params params = new Params();
      params.setErrorNotificationTitle("Local changes were not restored");
      params.setMergeDialogCustomizer(new UnstashMergeDialogCustomizer());
      params.setReverse(true);
      return params;
    }


    @Override
    protected void notifyUnresolvedRemain() {
      VcsNotifier.getInstance(myProject).notifyImportantWarning("Local changes were restored with conflicts",
                                                                "Your uncommitted changes were saved to <a href='saver'>stash</a>.<br/>" +
                                                                "Unstash is not complete, you have unresolved merges in your working tree<br/>" +
                                                                "<a href='resolve'>Resolve</a> conflicts and drop the stash.",
                                                                new NotificationListener() {
                                                                  @Override
                                                                  public void hyperlinkUpdate(@NotNull Notification notification,
                                                                                              @NotNull HyperlinkEvent event) {
                                                                    if (event.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
                                                                      if (event.getDescription().equals("saver")) {
                                                                        // we don't use #showSavedChanges to specify unmerged root first
                                                                        GitUnstashDialog.showUnstashDialog(myProject,
                                                                                                           new ArrayList<VirtualFile>(
                                                                                                             myStashedRoots),
                                                                                                           myStashedRoots.iterator().next()
                                                                        );
                                                                      }
                                                                      else if (event.getDescription().equals("resolve")) {
                                                                        mergeNoProceed();
                                                                      }
                                                                    }
                                                                  }
                                                                }
      );
    }

  }

  private static class UnstashMergeDialogCustomizer extends MergeDialogCustomizer {

    @Override
    public String getMultipleFileMergeDescription(@NotNull Collection<VirtualFile> files) {
      return "Uncommitted changes that were stashed before update have conflicts with updated files.";
    }

    @Override
    public String getLeftPanelTitle(@NotNull VirtualFile file) {
      return getConflictLeftPanelTitle();
    }

    @Override
    public String getRightPanelTitle(@NotNull VirtualFile file, VcsRevisionNumber revisionNumber) {
      return getConflictRightPanelTitle();
    }
  }
}
