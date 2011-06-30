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
import com.intellij.openapi.vcs.changes.shelf.ShelveChangesManager;
import com.intellij.openapi.vcs.changes.shelf.ShelvedChangeList;
import com.intellij.openapi.vcs.changes.shelf.ShelvedChangesViewManager;
import com.intellij.openapi.vcs.impl.LocalChangesUnderRoots;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.continuation.ContinuationContext;
import git4idea.i18n.GitBundle;
import git4idea.rollback.GitRollbackEnvironment;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

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
    final Map<VirtualFile,Collection<Change>> map = new LocalChangesUnderRoots(myProject).getChangesUnderRoots(rootsToSave);
    final List<Change> changes = new ArrayList<Change>();
    for (Collection<Change> changeCollection : map.values()) {
      changes.addAll(changeCollection);
    }
    if (! changes.isEmpty()) {
      String oldProgressTitle = myProgressIndicator.getText();
      myProgressIndicator.setText(GitBundle.getString("update.shelving.changes"));
      List<VcsException> exceptions = new ArrayList<VcsException>(1);
      myShelvedChangeList = GitShelveUtils.shelveChanges(myProject, myShelveManager, changes, myStashMessage, exceptions, false);
      myProgressIndicator.setText(oldProgressTitle);
      if (! exceptions.isEmpty()) {
        LOG.info("save " + exceptions, exceptions.get(0));
        throw exceptions.get(0);
      } else {
        for (VirtualFile root : rootsToSave) {
          GitRollbackEnvironment.resetHardLocal(myProject, root);
        }
      }
    }
  }

  protected void load(ContinuationContext context) {
    if (myShelvedChangeList != null) {
      LOG.info("load ");
      String oldProgressTitle = myProgressIndicator.getText();
      myProgressIndicator.setText(GitBundle.getString("update.unshelving.changes"));
      if (myShelvedChangeList != null) {
        GitShelveUtils.doSystemUnshelve(myProject, myShelvedChangeList, myShelveManager, context);
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
}
