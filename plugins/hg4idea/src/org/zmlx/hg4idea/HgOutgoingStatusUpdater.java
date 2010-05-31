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

import com.intellij.openapi.application.*;
import com.intellij.openapi.progress.*;
import com.intellij.openapi.project.*;
import com.intellij.openapi.vcs.*;
import org.jetbrains.annotations.*;
import org.zmlx.hg4idea.command.*;
import org.zmlx.hg4idea.ui.*;

import java.util.*;

class HgOutgoingStatusUpdater implements HgUpdater {

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
            List<HgRevisionNumber> outgoing = new LinkedList<HgRevisionNumber>();
            for (VcsRoot root : roots) {
              outgoing.addAll(command.execute(root.path));
            }
            status.setChanges(outgoing.size(), new OutgoingChangesetFormatter(outgoing));
            indicator.stop();
          }
        }.queue();
      }
    }, project.getDisposed());
  }

  private final class OutgoingChangesetFormatter implements HgChangesetStatus.ChangesetWriter {
    private final StringBuilder builder = new StringBuilder();

    private OutgoingChangesetFormatter(List<HgRevisionNumber> changesets) {
      builder.append("<html>");
      builder.append("<b>Outgoing changesets</b>:<br>");
      for (HgRevisionNumber revisionNumber : changesets) {
        builder
          .append(revisionNumber.asString()).append(" ")
          .append(revisionNumber.getCommitMessage()).append("<br>");
      }
      builder.append("</html>");
    }

    public String asString() {
      return builder.toString();
    }
  }

}
