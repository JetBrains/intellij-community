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

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ContentRevision;
import com.intellij.openapi.vcs.changes.LocalChangeList;
import com.intellij.openapi.vcs.changes.shelf.ShelveChangesManager;
import com.intellij.openapi.vcs.changes.shelf.ShelvedChangeList;
import com.intellij.openapi.vcs.changes.shelf.ShelvedChangesViewManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.continuation.ContinuationContext;
import git4idea.GitUtil;
import git4idea.i18n.GitBundle;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;

/**
 * @author Kirill Likhodedov
 */
public class GitShelveChangesSaver extends GitChangesSaver {
  private static final Logger LOG = Logger.getInstance(GitShelveChangesSaver.class);
  private final ShelveChangesManager myShelveManager;
  private final ShelvedChangesViewManager myShelveViewManager;
  private ShelvedChangeList myShelvedChangeList;

  public GitShelveChangesSaver(Project project, ProgressIndicator indicator, String stashMessage) {
    super(project, indicator, stashMessage);
    myShelveManager = ShelveChangesManager.getInstance(myProject);
    myShelveViewManager = ShelvedChangesViewManager.getInstance(myProject);
  }

  @Override
  protected void save(@NotNull Collection<VirtualFile> rootsToSave) throws VcsException {
    LOG.info("save " + rootsToSave);
    ArrayList<Change> changes = new ArrayList<Change>();
    for (LocalChangeList l : myChangeLists) {
      changes.addAll(filterChangesByRoots(l.getChanges(), rootsToSave)); // adding only changes from roots which are to be saved
    }
    if (!changes.isEmpty()) {
      String oldProgressTitle = myProgressIndicator.getText();
      myProgressIndicator.setText(GitBundle.getString("update.shelving.changes"));
      List<VcsException> exceptions = new ArrayList<VcsException>(1);
      myShelvedChangeList = GitStashUtils.shelveChanges(myProject, myShelveManager, changes, myStashMessage, exceptions);
      myProgressIndicator.setText(oldProgressTitle);
      if (!exceptions.isEmpty()) {
        LOG.info("save " + exceptions, exceptions.get(0));
        throw exceptions.get(0);
      }
    }
  }

  protected void load(Runnable restoreListsRunnable, ContinuationContext context) {
    if (myShelvedChangeList != null) {
      LOG.info("load ");
      String oldProgressTitle = myProgressIndicator.getText();
      myProgressIndicator.setText(GitBundle.getString("update.unshelving.changes"));
      if (myShelvedChangeList != null) {
        GitStashUtils.doSystemUnshelve(myProject, myShelvedChangeList, myShelveManager, restoreListsRunnable, context);
      }
      myProgressIndicator.setText(oldProgressTitle);
    }
  }

  @Override
  protected boolean wereChangesSaved() {
    return myShelvedChangeList != null;
  }

  @Override public String getSaverName() {
    return "shelf";
  }

  @Override protected void showSavedChanges() {
    myShelveViewManager.activateView(myShelvedChangeList);
  }

  /**
   * Goes through the changes and returns only those of them which belong to any of the given roots,
   * throwing away the changes which don't belong to any of the given roots.
   */
  private static @NotNull Collection<Change> filterChangesByRoots(@NotNull Collection<Change> changes, @NotNull Collection<VirtualFile> rootsToSave) {
    Collection<Change> filteredChanges = new HashSet<Change>();
    for (Change change : changes) {
      final ContentRevision beforeRevision = change.getBeforeRevision();
      if (beforeRevision != null) {
        final VirtualFile root = GitUtil.getGitRootOrNull(beforeRevision.getFile());
        if (root != null && rootsToSave.contains(root)) {
          filteredChanges.add(change);
          continue;
        }
      }

      final ContentRevision afterRevision = change.getAfterRevision();
      if (afterRevision != null) {
        final VirtualFile root = GitUtil.getGitRootOrNull(afterRevision.getFile());
        if (root != null && rootsToSave.contains(root)) {
          filteredChanges.add(change);
        }
      }
    }
    return filteredChanges;
  }

}
