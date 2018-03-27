// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.ui.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.AnActionExtensionProvider;
import com.intellij.openapi.vcs.VcsDataKeys;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.actions.CreatePatchFromChangesAction;
import com.intellij.vcs.log.VcsLogDataKeys;
import com.intellij.vcs.log.util.VcsLogUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;

public class VcsLogCreatePatchActionProvider implements AnActionExtensionProvider {
  private final boolean mySilentClipboard;

  private VcsLogCreatePatchActionProvider(boolean silentClipboard) {
    mySilentClipboard = silentClipboard;
  }

  public static class Dialog extends VcsLogCreatePatchActionProvider {
    public Dialog() {
      super(false);
    }
  }

  public static class Clipboard extends VcsLogCreatePatchActionProvider {
    public Clipboard() {
      super(true);
    }
  }

  @Override
  public boolean isActive(@NotNull AnActionEvent e) {
    return e.getData(VcsLogDataKeys.VCS_LOG_UI) != null;
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    Change[] changes = e.getData(VcsDataKeys.CHANGES);
    e.getPresentation().setEnabled(changes != null && changes.length > 0);
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    VcsLogUtil.triggerUsage(e);

    Change[] changes = e.getRequiredData(VcsDataKeys.CHANGES);
    String commitMessage = e.getData(VcsDataKeys.PRESET_COMMIT_MESSAGE);
    CreatePatchFromChangesAction.createPatch(e.getProject(), commitMessage, Arrays.asList(changes), mySilentClipboard);
  }
}
