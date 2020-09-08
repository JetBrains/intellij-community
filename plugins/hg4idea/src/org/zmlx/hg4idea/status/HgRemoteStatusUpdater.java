// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.zmlx.hg4idea.status;

import com.intellij.concurrency.JobScheduler;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.progress.util.BackgroundTaskUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.text.HtmlBuilder;
import com.intellij.openapi.util.text.HtmlChunk;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.messages.MessageBusConnection;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.zmlx.hg4idea.HgBundle;
import org.zmlx.hg4idea.HgGlobalSettings;
import org.zmlx.hg4idea.HgRevisionNumber;
import org.zmlx.hg4idea.HgVcs;
import org.zmlx.hg4idea.command.HgIncomingCommand;
import org.zmlx.hg4idea.command.HgOutgoingCommand;
import org.zmlx.hg4idea.status.ui.HgWidgetUpdater;
import org.zmlx.hg4idea.util.HgUtil;

import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class HgRemoteStatusUpdater implements Disposable {
  private final Project myProject;
  private final HgVcs myVcs;
  private final HgChangesetStatus myIncomingStatus = new HgChangesetStatus(HgBundle.message("hg4idea.changesets.in"));
  private final HgChangesetStatus myOutgoingStatus = new HgChangesetStatus(HgBundle.message("hg4idea.changesets.out"));
  private final AtomicBoolean myUpdateStarted = new AtomicBoolean();

  public HgRemoteStatusUpdater(@NotNull HgVcs vcs) {
    myProject = vcs.getProject();
    myVcs = vcs;

    MessageBusConnection busConnection = myProject.getMessageBus().connect(this);
    busConnection.subscribe(HgVcs.REMOTE_TOPIC, (project, root) -> updateInBackground(root));
    busConnection.subscribe(HgVcs.INCOMING_OUTGOING_CHECK_TOPIC, new HgWidgetUpdater() {
      @Override
      public void updateVisibility() {
        updateInBackground(null);
      }
    });

    int checkIntervalSeconds = HgGlobalSettings.getIncomingCheckIntervalSeconds();
    ScheduledFuture<?> future = JobScheduler.getScheduler().scheduleWithFixedDelay(() -> updateInBackground(null), 5,
                                                                                   checkIntervalSeconds, TimeUnit.SECONDS);
    Disposer.register(this, () -> future.cancel(true));
  }

  @Override
  public void dispose() {
  }

  public HgChangesetStatus getStatus(boolean isIncoming) {
    return isIncoming ? myIncomingStatus : myOutgoingStatus;
  }

  private void updateInBackground(@Nullable VirtualFile root) {
    if (!isCheckingEnabled(myProject)) return;
    if (!myUpdateStarted.compareAndSet(false, true)) return;
    new Task.Backgroundable(myProject, getProgressTitle(), true) {
      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        if (myProject.isDisposed()) return;
        final VirtualFile[] roots = root != null ? new VirtualFile[]{root}
                                                 : ProjectLevelVcsManager.getInstance(myProject).getRootsUnderVcs(myVcs);
        updateChangesStatusSynchronously(myProject, roots, myIncomingStatus, true);
        updateChangesStatusSynchronously(myProject, roots, myOutgoingStatus, false);

        BackgroundTaskUtil.syncPublisher(myProject, HgVcs.INCOMING_OUTGOING_CHECK_TOPIC).update();
      }

      @Override
      public void onFinished() {
        myUpdateStarted.set(false);
      }
    }.queue();
  }

  private static void updateChangesStatusSynchronously(Project project, VirtualFile[] roots, HgChangesetStatus status, boolean incoming) {
    if (!isCheckingEnabled(project)) return;
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

  @NlsContexts.ProgressTitle
  private static String getProgressTitle() {
    return HgBundle.message("hg4idea.changesets.checking.progress");
  }

  public static boolean isCheckingEnabled(@NotNull Project project) {
    HgVcs hgVcs = HgVcs.getInstance(project);
    if (hgVcs == null) return false;
    if (HgUtil.getRepositoryManager(project).getRepositories().isEmpty()) return false;
    return hgVcs.getProjectSettings().isCheckIncomingOutgoing();
  }

  private static final class ChangesetFormatter implements HgChangesetStatus.ChangesetWriter {
    private final @Nls String string;

    private ChangesetFormatter(HgChangesetStatus status, List<HgRevisionNumber> changesets) {
      HtmlBuilder sb = new HtmlBuilder();
      sb.append(HtmlChunk.text(HgBundle.message("hg4idea.widget.tooltip.title.status.changesets", status.getStatusName())).bold())
        .append(":").br();
      for (HgRevisionNumber revisionNumber : changesets) {
        sb.append(revisionNumber.asString()).append(" ")
          .append(revisionNumber.getCommitMessage())
          .append(" (").append(revisionNumber.getAuthor()).append(")")
          .br();
      }
      string = sb.wrapWithHtmlBody().toString();
    }

    @Override
    public String asString() {
      return string;
    }
  }
}
