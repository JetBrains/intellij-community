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

import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.LocalChangeList;
import com.intellij.openapi.vcs.changes.shelf.ShelveChangesManager;
import com.intellij.openapi.vcs.changes.shelf.ShelvedChangeList;
import git4idea.i18n.GitBundle;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Kirill Likhodedov
 */
public class GitShelveChangesSaver extends GitChangesSaver {
  private final ShelveChangesManager myShelveManager;
  private ShelvedChangeList myShelvedChangeList;

  protected GitShelveChangesSaver(Project project, ProgressIndicator indicator, String stashMessage) {
    super(project, indicator, stashMessage);
    myShelveManager = ShelveChangesManager.getInstance(myProject);
  }

  @Override
  protected void save() throws VcsException {
    ArrayList<Change> changes = new ArrayList<Change>();
    for (LocalChangeList l : myChangeLists) {
      changes.addAll(l.getChanges());
    }
    if (changes.size() > 0) {
      myProgressIndicator.setText(GitBundle.getString("update.shelving.changes"));
      List<VcsException> exceptions = new ArrayList<VcsException>(1);
      myShelvedChangeList = GitStashUtils.shelveChanges(myProject, myShelveManager, changes, myStashMessage, exceptions);
      if (!exceptions.isEmpty()) {
        throw exceptions.get(0);
      }
    }
  }

  protected void load() throws VcsException {
    if (myShelvedChangeList != null) {
      myProgressIndicator.setText(GitBundle.getString("update.unshelving.changes"));
      if (myShelvedChangeList != null) {
        List<VcsException> exceptions = new ArrayList<VcsException>(1);
        GitStashUtils.doSystemUnshelve(myProject, myShelvedChangeList, myShelveManager, myChangeManager, exceptions);
        if (!exceptions.isEmpty()) {
          throw exceptions.get(0);
        }
      }
    }
  }
  
}
