// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.ui.actions;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.AnActionExtensionProvider;
import com.intellij.openapi.vcs.VcsDataKeys;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.actions.CreatePatchFromChangesAction;
import com.intellij.openapi.vcs.changes.ui.ChangesBrowserBase;
import com.intellij.vcs.log.VcsLogCommitSelection;
import com.intellij.vcs.log.VcsLogDataKeys;
import com.intellij.vcs.log.statistics.VcsLogUsageTriggerCollector;
import com.intellij.vcs.log.ui.table.VcsLogCommitSelectionUtils;
import com.intellij.vcs.log.util.VcsLogUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.List;

@ApiStatus.Internal
public class VcsLogCreatePatchActionProvider implements AnActionExtensionProvider {
  private final boolean mySilentClipboard;

  private VcsLogCreatePatchActionProvider(boolean silentClipboard) {
    mySilentClipboard = silentClipboard;
  }

  @ApiStatus.Internal
  public static class Dialog extends VcsLogCreatePatchActionProvider {
    public Dialog() {
      super(false);
    }
  }

  @ApiStatus.Internal
  public static class Clipboard extends VcsLogCreatePatchActionProvider {
    public Clipboard() {
      super(true);
    }
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public boolean isActive(@NotNull AnActionEvent e) {
    return e.getData(VcsLogDataKeys.VCS_LOG_UI) != null;
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    e.getPresentation().setEnabled(isEnabled(e));
  }

  private static boolean isEnabled(@NotNull AnActionEvent e) {
    if (e.getData(ChangesBrowserBase.DATA_KEY) != null) {
      Change[] changes = e.getData(VcsDataKeys.CHANGES);
      return changes != null && changes.length > 0;
    }
    VcsLogCommitSelection selection = e.getData(VcsLogDataKeys.VCS_LOG_COMMIT_SELECTION);
    return selection != null && VcsLogCommitSelectionUtils.isNotEmpty(selection);
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    VcsLogUsageTriggerCollector.triggerUsage(e, this);

    String commitMessage = e.getData(VcsDataKeys.PRESET_COMMIT_MESSAGE);

    if (e.getData(ChangesBrowserBase.DATA_KEY) != null) {
      Change[] changes = e.getData(VcsDataKeys.CHANGES);
      if (changes == null) return;
      CreatePatchFromChangesAction.createPatch(e.getProject(), commitMessage, Arrays.asList(changes), mySilentClipboard);
      return;
    }

    VcsLogCommitSelection selection = e.getData(VcsLogDataKeys.VCS_LOG_COMMIT_SELECTION);
    if (selection == null) return;

    selection.requestFullDetails(details -> {
      List<Change> changes = VcsLogUtil.collectChanges(details);
      CreatePatchFromChangesAction.createPatch(e.getProject(), commitMessage, changes, mySilentClipboard);
    });
  }
}
