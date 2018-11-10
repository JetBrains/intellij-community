// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.zmlx.hg4idea.status;

import com.intellij.concurrency.JobScheduler;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.progress.util.BackgroundTaskUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.xml.util.XmlStringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.zmlx.hg4idea.*;
import org.zmlx.hg4idea.command.HgIncomingCommand;
import org.zmlx.hg4idea.command.HgOutgoingCommand;

import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class HgRemoteStatusUpdater implements HgUpdater {

  private final AbstractVcs myVcs;
  private final HgChangesetStatus myIncomingStatus;
  private final HgChangesetStatus myOutgoingStatus;
  private final HgProjectSettings myProjectSettings;
  private final AtomicBoolean myUpdateStarted = new AtomicBoolean();

  private MessageBusConnection busConnection;

  private ScheduledFuture<?> changesUpdaterScheduledFuture;


  public HgRemoteStatusUpdater(@NotNull HgVcs vcs,
                               HgChangesetStatus incomingStatus,
                               HgChangesetStatus outgoingStatus,
                               HgProjectSettings projectSettings) {
    myVcs = vcs;
    myIncomingStatus = incomingStatus;
    myOutgoingStatus = outgoingStatus;
    myProjectSettings = projectSettings;
  }

  public void update(final Project project) {
    update(project, null);
  }

  @Override
  public void update(final Project project, @Nullable final VirtualFile root) {
    if (!isCheckingEnabled() || myUpdateStarted.get()) {
      return;
    }
    myUpdateStarted.set(true);
    ApplicationManager.getApplication().invokeLater(() -> new Task.Backgroundable(project, getProgressTitle(), true) {
      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        if (project.isDisposed()) return;
        final VirtualFile[] roots =
          root != null ? new VirtualFile[]{root} : ProjectLevelVcsManager.getInstance(project).getRootsUnderVcs(myVcs);
        updateChangesStatusSynchronously(project, roots, myIncomingStatus, true);
        updateChangesStatusSynchronously(project, roots, myOutgoingStatus, false);

        BackgroundTaskUtil.syncPublisher(project, HgVcs.INCOMING_OUTGOING_CHECK_TOPIC).update();

        indicator.stop();
        myUpdateStarted.set(false);
      }
    }.queue());
  }


  public void activate() {
    busConnection = myVcs.getProject().getMessageBus().connect();
    busConnection.subscribe(HgVcs.REMOTE_TOPIC, this);

    int checkIntervalSeconds = HgGlobalSettings.getIncomingCheckIntervalSeconds();
    changesUpdaterScheduledFuture = JobScheduler.getScheduler().scheduleWithFixedDelay(() -> update(myVcs.getProject()), 5, checkIntervalSeconds, TimeUnit.SECONDS);
  }

  public void deactivate() {
    busConnection.disconnect();

    if (changesUpdaterScheduledFuture != null) {
      changesUpdaterScheduledFuture.cancel(true);
    }
  }

  private void updateChangesStatusSynchronously(Project project, VirtualFile[] roots, HgChangesetStatus status, boolean incoming) {
    if (!myProjectSettings.isCheckIncomingOutgoing()) return;
    final List<HgRevisionNumber> changesets = new LinkedList<>();
    for (VirtualFile root : roots) {
      if (incoming) {
        changesets.addAll(new HgIncomingCommand(project).executeInCurrentThread(root));
      }
      else {
        changesets.addAll(new HgOutgoingCommand(project).executeInCurrentThread(root));
      }
    }
    status.setChanges(changesets.size(), new ChangesetFormatter(status, changesets));
  }

  private static String getProgressTitle() {
    return "Checking Incoming and Outgoing Changes";
  }

  protected boolean isCheckingEnabled() {
    return myProjectSettings.isCheckIncomingOutgoing();
  }

  private static final class ChangesetFormatter implements HgChangesetStatus.ChangesetWriter {
    private final String string;

    private ChangesetFormatter(HgChangesetStatus status, List<HgRevisionNumber> changesets) {
      StringBuilder builder = new StringBuilder();
      builder.append("<b>").append(status.getStatusName()).append(" changesets</b>:<br>");
      for (HgRevisionNumber revisionNumber : changesets) {
        builder.append(revisionNumber.asString()).append(" ").append(revisionNumber.getCommitMessage()).append(" (")
          .append(revisionNumber.getAuthor()).append(")<br>");
      }
      string = XmlStringUtil.wrapInHtml(builder);
    }

    @Override
    public String asString() {
      return string;
    }
  }
}
