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

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.VcsRoot;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.application.ApplicationManager;
import org.zmlx.hg4idea.command.HgOutgoingCommand;
import org.zmlx.hg4idea.ui.HgChangesetStatus;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.LinkedList;
import java.util.List;

class HgOutgoingStatusUpdater implements HgUpdater {

  private static final int LIMIT = 10;

  private final HgChangesetStatus status;
  private final HgProjectSettings projectSettings;

  public HgOutgoingStatusUpdater(HgChangesetStatus status, HgProjectSettings projectSettings) {
    this.status = status;
    this.projectSettings = projectSettings;
  }

  public void update(final Project project) {
    if (!projectSettings.isCheckOutgoing()) {
      return;
    }
    ApplicationManager.getApplication().invokeLater(new Runnable() {
      public void run() {
        new Task.Backgroundable(project, "Checking Outgoing Changesets", true) {
          public void run(@NotNull ProgressIndicator indicator) {
            if (project.isDisposed()) return;
            HgOutgoingCommand command = new HgOutgoingCommand(project);
            VcsRoot[] roots = ProjectLevelVcsManager.getInstance(project).getAllVcsRoots();
            List<HgFileRevision> outgoing = new LinkedList<HgFileRevision>();
            for (VcsRoot root : roots) {
              HgFile hgFile = new HgFile(root.path, new File("."));
              outgoing.addAll(command.execute(hgFile, LIMIT - outgoing.size()));
            }
            status.setChanges(outgoing.size(), new OutgoingChangesetFormatter(outgoing));
          }
        }.queue();
      }
    }, project.getDisposed());
  }

  private final class OutgoingChangesetFormatter implements HgChangesetStatus.ChangesetWriter {
    private final StringBuilder builder = new StringBuilder();

    private OutgoingChangesetFormatter(List<HgFileRevision> changesets) {
      builder.append("<html>");
      builder.append("<b>Outgoing changesets</b>:<br>");
      for (HgFileRevision changeset : changesets) {
        builder
          .append(changeset.getRevisionNumber().asString()).append(" ")
          .append(changeset.getCommitMessage()).append("<br>");
      }
      builder.append("</html>");
    }

    public String asString() {
      return builder.toString();
    }
  }

}
