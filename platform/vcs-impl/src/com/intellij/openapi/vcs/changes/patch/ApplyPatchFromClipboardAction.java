// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.patch;

import com.intellij.ide.IdeEventQueue;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.application.ex.ClipboardUtil;
import com.intellij.openapi.client.ClientAppSession;
import com.intellij.openapi.client.ClientSessionsManager;
import com.intellij.openapi.diff.impl.patch.PatchReader;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.VcsApplicationSettings;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.VcsNotifier;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.testFramework.LightVirtualFile;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.KeyEvent;
import java.util.Collections;

import static com.intellij.openapi.vcs.VcsNotificationIdsHolder.PATCH_APPLY_NOT_PATCH_FILE;

@ApiStatus.Internal
public class ApplyPatchFromClipboardAction extends DumbAwareAction {

  @Override
  public void update(@NotNull AnActionEvent e) {
    Project project = e.getData(CommonDataKeys.PROJECT);

    ClientAppSession appSession = ClientSessionsManager.getAppSession();
    // In a remote development case we cannot receive clipboard content immediately (we need to fetch it from client),
    // so we make the action enabled unconditionally.
    String text = (appSession != null && appSession.isRemote()) ? "" : ClipboardUtil.getTextInClipboard();

    // allow to apply from clipboard even if we do not detect it as a patch, because during applying we parse content more precisely
    e.getPresentation().setEnabled(project != null && text != null && ChangeListManager.getInstance(project).isFreezed() == null);
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return IdeEventQueue.getInstance().isDispatchingOnMainThread ? ActionUpdateThread.EDT : ActionUpdateThread.BGT;
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    Project project = e.getData(CommonDataKeys.PROJECT);
    if (project == null) return;
    if (ChangeListManager.getInstance(project).isFreezedWithNotification(VcsBundle.message("patch.apply.cannot.apply.now"))) return;
    FileDocumentManager.getInstance().saveAllDocuments();

    String clipboardText = StringUtil.notNullize(ClipboardUtil.getTextInClipboard());
    if (PatchReader.isPatchContent(clipboardText)) {
      new MyApplyPatchFromClipboardDialog(project, clipboardText).show();
    }
    else {
      VcsNotifier.getInstance(project).notifyWeakError(PATCH_APPLY_NOT_PATCH_FILE, VcsBundle.message("patch.apply.not.patch.clipboard"));
    }
  }

  public static class MyApplyPatchFromClipboardDialog extends ApplyPatchDifferentiatedDialog {

    public MyApplyPatchFromClipboardDialog(@NotNull Project project, @NotNull String clipboardText) {
      super(project, new ApplyPatchDefaultExecutor(project), Collections.emptyList(), ApplyPatchMode.APPLY_PATCH_IN_MEMORY,
            new LightVirtualFile("clipboardPatchFile", clipboardText), null, null, //NON-NLS
            null, null, null, false);
    }

    @Nullable
    @Override
    protected JComponent createDoNotAskCheckbox() {
      return createAnalyzeOnTheFlyOptionPanel();
    }

    @NotNull
    private static JCheckBox createAnalyzeOnTheFlyOptionPanel() {
      final JCheckBox removeOptionCheckBox = new JCheckBox(VcsBundle.message("patch.apply.analyze.from.clipboard.on.the.fly.checkbox"));
      removeOptionCheckBox.setMnemonic(KeyEvent.VK_L);
      removeOptionCheckBox.setSelected(VcsApplicationSettings.getInstance().DETECT_PATCH_ON_THE_FLY);
      removeOptionCheckBox.addActionListener(
        e -> VcsApplicationSettings.getInstance().DETECT_PATCH_ON_THE_FLY = removeOptionCheckBox.isSelected());
      return removeOptionCheckBox;
    }
  }
}
