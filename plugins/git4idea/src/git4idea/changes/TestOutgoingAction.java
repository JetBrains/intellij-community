/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package git4idea.changes;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.CurrentContentRevision;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.openapi.vcs.versionBrowser.CommittedChangeList;
import com.intellij.openapi.vfs.VirtualFile;
import git4idea.GitVcs;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class TestOutgoingAction extends AnAction {
  @Override
  public void actionPerformed(AnActionEvent e) {
    final Project project = PlatformDataKeys.PROJECT.getData(e.getDataContext());
    if (project == null) return;
    final GitVcs vcs = GitVcs.getInstance(project);

    final VcsOutgoingChangesProvider<CommittedChangeList> provider = vcs.getOutgoingChangesProvider();
    final ProjectLevelVcsManager vcsManager = ProjectLevelVcsManager.getInstance(project);
    final VirtualFile vf = PlatformDataKeys.VIRTUAL_FILE.getData(e.getDataContext());
    try {
      if (vf != null) {
        final VirtualFile root = vcsManager.getVcsRootFor(vf);
        final Pair<VcsRevisionNumber, List<CommittedChangeList>> revisionNumberListPair = provider.getOutgoingChanges(root, true);
        final VcsRevisionNumber revisionNumber = provider.getMergeBaseNumber(vf);
        System.out.println("revisionNumber = " + revisionNumber);

        final CurrentContentRevision contentRevision = new CurrentContentRevision(new FilePathImpl(vf));
        final Collection<Pair<VcsRevisionNumber,List<CommittedChangeList>>> rootsForChanges =
          OutgoingChangesUtil.getVcsRootsForChanges(vcs, Collections.singletonList(new Change(contentRevision, contentRevision)));
      }
    }
    catch (VcsException e1) {
      e1.printStackTrace();
    }

    final VirtualFile[] roots = vcsManager.getRootsUnderVcs(vcs);
    if (roots == null) return;
    for (VirtualFile root : roots) {
      try {
        final Pair<VcsRevisionNumber, List<CommittedChangeList>> pair = provider.getOutgoingChanges(root, true);
        final VcsRevisionNumber number = provider.getMergeBaseNumber(root);
        System.out.println("list.size() = " + pair.getSecond().size() + " number = " + number);
        assert number.equals(pair.getFirst());
      }
      catch (VcsException e1) {
        e1.printStackTrace();
      }
    }
  }

  @Override
  public void update(AnActionEvent e) {
    e.getPresentation().setText("Test Outgoing Changes");
  }
}
