// Copyright 2008-2010 Victor Iacoban
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software distributed under
// the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
// either express or implied. See the License for the specific language governing permissions and
// limitations under the License.
package org.zmlx.hg4idea;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.VcsRoot;
import org.jetbrains.annotations.NotNull;
import org.zmlx.hg4idea.command.HgIncomingCommand;
import org.zmlx.hg4idea.ui.HgChangesetStatus;

import java.util.LinkedList;
import java.util.List;

class HgIncomingStatusUpdater implements HgUpdater {

  private final HgChangesetStatus status;
  private final HgProjectSettings projectSettings;

  public HgIncomingStatusUpdater(HgChangesetStatus status, HgProjectSettings projectSettings) {
    this.status = status;
    this.projectSettings = projectSettings;
  }

  public void update(final Project project) {
    if (!projectSettings.isCheckIncoming()) {
      return;
    }
    ApplicationManager.getApplication().invokeLater(new Runnable() {
      public void run() {
        new Task.Backgroundable(project, "Checking Incoming Changesets", true) {
          public void run(@NotNull ProgressIndicator indicator) {
            if (project.isDisposed()) return;
            HgIncomingCommand command = new HgIncomingCommand(project);
            VcsRoot[] roots = ProjectLevelVcsManager.getInstance(project).getAllVcsRoots();
            List<HgRevisionNumber> changesets = new LinkedList<HgRevisionNumber>();
            for (VcsRoot root : roots) {
              changesets.addAll(command.execute(root.path));
            }
            status.setChanges(changesets.size(), new IncomingChangesetFormatter(changesets));
          }
        }.queue();
      }
    }, project.getDisposed());
  }

  private final class IncomingChangesetFormatter implements HgChangesetStatus.ChangesetWriter {
    private final StringBuilder builder = new StringBuilder();

    private IncomingChangesetFormatter(List<HgRevisionNumber> changesets) {
      builder.append("<html>");
      builder.append("<b>Incoming changesets</b>:<br>");
      for (HgRevisionNumber changeset : changesets) {
        builder
          .append(changeset.asString()).append(" ")
          .append(changeset.getCommitMessage()).append(" ")
          .append("(").append(changeset.getAuthor()).append(")<br>");
      }
      builder.append("</html>");
    }

    public String asString() {
      return builder.toString();
    }
  }

}
