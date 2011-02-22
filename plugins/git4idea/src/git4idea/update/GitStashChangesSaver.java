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
import com.intellij.openapi.vcs.ObjectsConvertor;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ContentRevision;
import com.intellij.openapi.vcs.changes.LocalChangeList;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import git4idea.GitUtil;
import git4idea.commands.GitHandlerUtil;
import git4idea.config.GitVcsSettings;
import git4idea.convert.GitFileSeparatorConverter;

import java.io.File;
import java.util.*;

/**
 * @author Kirill Likhodedov
 */
public class GitStashChangesSaver extends GitChangesSaver {

  private static final Logger LOG = Logger.getInstance(GitStashChangesSaver.class);
  private final Set<VirtualFile> myStashedRoots = new HashSet<VirtualFile>(); // save stashed roots to unstash only them

  GitStashChangesSaver(Project project, ProgressIndicator progressIndicator, String stashMessage) {
    super(project, progressIndicator, stashMessage);
  }

  @Override
  protected void save() throws VcsException {
    Map<VirtualFile, Collection<Change>> changes = groupChangesByRoots();
    convertSeparatorsIfNeeded(changes);
    stash(changes.keySet());
  }

  @Override
  protected void load() throws VcsException {
    for (VirtualFile root : myStashedRoots) {
      loadRoot(root);
    }
    final List<File> files = ObjectsConvertor.fp2jiof(getChangedFiles());
    LocalFileSystem.getInstance().refreshIoFiles(files);
  }

  private void stash(Collection<VirtualFile> roots) throws VcsException {
    for (VirtualFile root : roots) {
      final String message = GitHandlerUtil.formatOperationName("Stashing changes from", root);
      LOG.info(message);
      myProgressIndicator.setText(message);
      if (GitStashUtils.saveStash(myProject, root, myStashMessage)) {
        myStashedRoots.add(root);
      }
    }
  }

  private void convertSeparatorsIfNeeded(Map<VirtualFile, Collection<Change>> changes) throws VcsException {
    GitVcsSettings settings = GitVcsSettings.getInstance(myProject);
    if (settings != null) {
      List<VcsException> exceptions = new ArrayList<VcsException>(1);
      GitFileSeparatorConverter.convertSeparatorsIfNeeded(myProject, settings, changes, exceptions);
      if (!exceptions.isEmpty()) {
        throw exceptions.get(0);
      }
    }
  }

  private void loadRoot(VirtualFile root) throws VcsException {
    myProgressIndicator.setText(GitHandlerUtil.formatOperationName("Unstashing changes to", root));
    GitStashUtils.popLastStash(myProject, root);
    // TODO: detect conflict when unstashing and offer a reverse merge.
    //GitUpdater.mergeFiles(myProject, root, true);
  }

  // Sort changes from myChangesLists by their git roots.
  private Map<VirtualFile, Collection<Change>> groupChangesByRoots() {
    final Map<VirtualFile, Collection<Change>> sortedChanges = new HashMap<VirtualFile, Collection<Change>>();
    for (LocalChangeList l : myChangeLists) {
      final Collection<Change> changeCollection = l.getChanges();
      for (Change c : changeCollection) {
        if (c.getAfterRevision() != null) {
          storeChangeInMap(sortedChanges, c, c.getAfterRevision());
        } else if (c.getBeforeRevision() != null) {
            storeChangeInMap(sortedChanges, c, c.getBeforeRevision());
        }
      }
    }
    return sortedChanges;
  }

  private static void storeChangeInMap(Map<VirtualFile, Collection<Change>> sortedChanges, Change c, ContentRevision before) {
    final VirtualFile root = GitUtil.getGitRootOrNull(before.getFile());
    if (root != null) {
      Collection<Change> changes = sortedChanges.get(root);
      if (changes == null) {
        changes = new ArrayList<Change>();
        sortedChanges.put(root, changes);
      }
      changes.add(c);
    }
  }

}
