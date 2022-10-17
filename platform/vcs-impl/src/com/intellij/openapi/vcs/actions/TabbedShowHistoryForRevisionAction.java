// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.actions;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ContentRevision;
import com.intellij.openapi.vcs.history.VcsHistoryProvider;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.openapi.vcs.impl.AbstractVcsHelperImpl;
import com.intellij.vcs.history.VcsHistoryProviderEx;
import com.intellij.vcs.log.VcsLogFileHistoryProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.Objects;

import static com.intellij.util.ObjectUtils.tryCast;


public class TabbedShowHistoryForRevisionAction extends DumbAwareAction {

  @Override
  public void update(@NotNull AnActionEvent e) {
    super.update(e);
    boolean visible = isVisible(e);
    e.getPresentation().setVisible(visible);
    e.getPresentation().setEnabled(visible && isEnabled(e));
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent event) {
    Project project = event.getRequiredData(CommonDataKeys.PROJECT);
    AbstractVcs vcs = Objects.requireNonNull(getVcs(project, event.getData(VcsDataKeys.VCS)));

    Pair<FilePath, VcsRevisionNumber> fileAndRevision = Objects.requireNonNull(getFileAndRevision(event));
    FilePath file = fileAndRevision.getFirst();
    VcsRevisionNumber revisionNumber = fileAndRevision.getSecond();

    String revisionNumberString = revisionNumber.asString();
    if (canShowNewFileHistory(project, file, revisionNumberString)) {
      showNewFileHistory(project, file, revisionNumberString);
    }
    else {
      VcsHistoryProviderEx vcsHistoryProvider = Objects.requireNonNull((VcsHistoryProviderEx)vcs.getVcsHistoryProvider());
      AbstractVcsHelperImpl helper = Objects.requireNonNull(getVcsHelper(project));
      helper.showFileHistory(vcsHistoryProvider, file, vcs, revisionNumber);
    }
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  private static void showNewFileHistory(@NotNull Project project, @NotNull FilePath path, @NotNull String revisionNumber) {
    VcsLogFileHistoryProvider historyProvider = project.getService(VcsLogFileHistoryProvider.class);
    historyProvider.showFileHistory(Collections.singletonList(path), revisionNumber);
  }

  private static boolean canShowNewFileHistory(@NotNull Project project, @NotNull FilePath path, @NotNull String revisionNumber) {
    VcsLogFileHistoryProvider historyProvider = project.getService(VcsLogFileHistoryProvider.class);
    return historyProvider != null && historyProvider.canShowFileHistory(Collections.singletonList(path), revisionNumber);
  }

  private static boolean isVisible(@NotNull AnActionEvent event) {
    Project project = event.getProject();
    if (project == null || project.isDisposed()) return false;
    if (getVcsHelper(project) == null) return false;
    AbstractVcs vcs = getVcs(project, event.getData(VcsDataKeys.VCS));
    if (vcs == null) return false;
    VcsHistoryProvider vcsHistoryProvider = vcs.getVcsHistoryProvider();
    if (!(vcsHistoryProvider instanceof VcsHistoryProviderEx)) return false;
    return true;
  }

  private static boolean isEnabled(@NotNull AnActionEvent event) {
    Pair<FilePath, VcsRevisionNumber> fileAndRevision = getFileAndRevision(event);
    return fileAndRevision != null && !fileAndRevision.second.asString().isEmpty();
  }

  @Nullable
  private static Pair<FilePath, VcsRevisionNumber> getFileAndRevision(@NotNull AnActionEvent event) {
    Change[] changes = event.getData(VcsDataKeys.SELECTED_CHANGES);
    if (changes == null || changes.length != 1) return null;
    Change change = changes[0];
    Pair<FilePath, VcsRevisionNumber> fileAndRevision = getFileAndRevision(change);
    if (fileAndRevision == null ||
        (change.getType() != Change.Type.DELETED && !fileAndRevision.second.asString().isEmpty())) return fileAndRevision;

    Project project = event.getProject();
    if (project == null ||
        !canShowNewFileHistory(project, fileAndRevision.getFirst(), fileAndRevision.getSecond().asString())) return fileAndRevision;
    VcsRevisionNumber revisionNumber = event.getData(VcsDataKeys.VCS_REVISION_NUMBER);
    if (revisionNumber == null) return fileAndRevision;

    return Pair.create(fileAndRevision.getFirst(), revisionNumber);
  }

  @Nullable
  private static Pair<FilePath, VcsRevisionNumber> getFileAndRevision(@NotNull Change change) {
    ContentRevision revision = change.getType() == Change.Type.DELETED ? change.getBeforeRevision() : change.getAfterRevision();
    if (revision == null) return null;
    return Pair.create(revision.getFile(), revision.getRevisionNumber());
  }

  @Nullable
  private static AbstractVcs getVcs(@NotNull Project project, @Nullable VcsKey vcsKey) {
    return vcsKey == null ? null : ProjectLevelVcsManager.getInstance(project).findVcsByName(vcsKey.getName());
  }

  @Nullable
  private static AbstractVcsHelperImpl getVcsHelper(@NotNull Project project) {
    AbstractVcsHelper helper = AbstractVcsHelper.getInstance(project);
    return tryCast(helper, AbstractVcsHelperImpl.class);
  }
}
