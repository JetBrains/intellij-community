package com.intellij.openapi.vcs.changes.patch;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.application.ex.ClipboardUtil;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.VcsApplicationSettings;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.testFramework.LightVirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.util.Collections;

public class ApplyPatchFromClipboardAction extends DumbAwareAction {

  @Override
  public void update(AnActionEvent e) {
    Project project = e.getData(CommonDataKeys.PROJECT);
    String text = ClipboardUtil.getTextInClipboard();
    // allow to apply from clipboard even if we do not detect it as a patch, because during applying we parse content more precisely
    e.getPresentation().setEnabled(project != null && text != null && ChangeListManager.getInstance(project).isFreezed() == null);
  }

  public void actionPerformed(AnActionEvent e) {
    final Project project = e.getRequiredData(CommonDataKeys.PROJECT);
    if (ChangeListManager.getInstance(project).isFreezedWithNotification("Can not apply patch now")) return;
    FileDocumentManager.getInstance().saveAllDocuments();

    String clipboardText = ClipboardUtil.getTextInClipboard();
    assert clipboardText != null;
    new MyApplyPatchFromClipboardDialog(project, clipboardText).show();
  }

  public static class MyApplyPatchFromClipboardDialog extends ApplyPatchDifferentiatedDialog {

    public MyApplyPatchFromClipboardDialog(@NotNull Project project, @NotNull String clipboardText) {
      super(project, new ApplyPatchDefaultExecutor(project), Collections.emptyList(), ApplyPatchMode.APPLY_PATCH_IN_MEMORY,
            new LightVirtualFile("clipboardPatchFile", clipboardText), null, null,
            null, null, null, false);
    }

    @Nullable
    @Override
    protected JComponent createDoNotAskCheckbox() {
      return createAnalyzeOnTheFlyOptionPanel();
    }

    @NotNull
    private static JCheckBox createAnalyzeOnTheFlyOptionPanel() {
      final JCheckBox removeOptionCheckBox = new JCheckBox("Analyze and Apply Patch from Clipboard on the Fly");
      removeOptionCheckBox.setMnemonic(KeyEvent.VK_L);
      removeOptionCheckBox.setSelected(VcsApplicationSettings.getInstance().DETECT_PATCH_ON_THE_FLY);
      removeOptionCheckBox.addActionListener(new ActionListener() {
        @Override
        public void actionPerformed(ActionEvent e) {
          VcsApplicationSettings.getInstance().DETECT_PATCH_ON_THE_FLY = removeOptionCheckBox.isSelected();
        }
      });
      return removeOptionCheckBox;
    }
  }
}
