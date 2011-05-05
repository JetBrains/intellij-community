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
package org.zmlx.hg4idea;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.zmlx.hg4idea.command.HgIncomingCommand;
import org.zmlx.hg4idea.command.HgOutgoingCommand;
import org.zmlx.hg4idea.ui.HgChangesetStatus;

import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author Kirill Likhodedov
 */
class HgRemoteStatusUpdater implements HgUpdater {

  private final HgChangesetStatus myIncomingStatus;
  private final HgChangesetStatus myOutgoingStatus;
  private final HgProjectSettings myProjectSettings;
  private final AtomicBoolean myUpdateStarted = new AtomicBoolean();
  private final AbstractVcs myVcs;

  public HgRemoteStatusUpdater(@NotNull HgVcs vcs, HgChangesetStatus incomingStatus, HgChangesetStatus outgoingStatus, HgProjectSettings projectSettings) {
    myVcs = vcs;
    myIncomingStatus = incomingStatus;
    myOutgoingStatus = outgoingStatus;
    myProjectSettings = projectSettings;
  }

  public void update(final Project project) {
    if (!isCheckingEnabled() || myUpdateStarted.get()) {
      return;
    }
    myUpdateStarted.set(true);
    ApplicationManager.getApplication().invokeLater(new Runnable() {
      public void run() {
        new Task.Backgroundable(project, getProgressTitle(), true) {
          public void run(@NotNull ProgressIndicator indicator) {
            if (project.isDisposed()) return;
            VirtualFile[] roots = ProjectLevelVcsManager.getInstance(project).getRootsUnderVcs(myVcs);
            if (myProjectSettings.isCheckIncoming()) {
              updateChangesetStatus(project, roots, myIncomingStatus, true);
            }
            if (myProjectSettings.isCheckOutgoing()) {
              updateChangesetStatus(project, roots, myOutgoingStatus, false);
            }
            indicator.stop();
            myUpdateStarted.set(false);
          }
        }.queue();
      }
    });
  }

  private void updateChangesetStatus(Project project, VirtualFile[] roots, HgChangesetStatus status, boolean incoming) {
    final List<HgRevisionNumber> changesets = new LinkedList<HgRevisionNumber>();
    for (VirtualFile root : roots) {
      if (incoming) {
        changesets.addAll(new HgIncomingCommand(project).execute(root));
      } else {
        changesets.addAll(new HgOutgoingCommand(project).execute(root));
      }
    }
    status.setChanges(changesets.size(), new ChangesetFormatter(status, changesets));
  }

  private String getProgressTitle() {
    String type;
    if (myProjectSettings.isCheckIncoming()) {
      if (myProjectSettings.isCheckOutgoing()) {
        type = "incoming and outgoing";
      } else {
        type = "incoming";
      }
    } else {
      type = "outgoing";
    }
    return "Checking " + type + " changes";
  }

  protected boolean isCheckingEnabled() {
    return myProjectSettings.isCheckIncoming() || myProjectSettings.isCheckOutgoing();
  }

  private final class ChangesetFormatter implements HgChangesetStatus.ChangesetWriter {
    private final StringBuilder builder = new StringBuilder();

    private ChangesetFormatter(HgChangesetStatus status, List<HgRevisionNumber> changesets) {
      builder.append("<html>");
      builder.append("<b>").append(status.getStatusName()).append(" changesets</b>:<br>");
      for (HgRevisionNumber revisionNumber : changesets) {
        builder
          .append(revisionNumber.asString()).append(" ")
          .append(revisionNumber.getCommitMessage())
          .append(" (").append(revisionNumber.getAuthor()).append(")<br>");
      }
      builder.append("</html>");
    }

    public String asString() {
      return builder.toString();
    }
  }
}
