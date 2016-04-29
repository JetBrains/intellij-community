package com.intellij.openapi.vcs.changes.patch;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.application.ex.ClipboardUtil;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.LightVirtualFile;

import java.util.Collections;

public class ApplyPatchFromClipboardAction extends DumbAwareAction {

  @Override
  public void update(AnActionEvent e) {
    Project project = e.getData(CommonDataKeys.PROJECT);
    String text = ClipboardUtil.getTextInClipboard();
    e.getPresentation().setEnabled(project != null && PatchFileUtil.isPatchContent(text));
  }

  public void actionPerformed(AnActionEvent e) {
    final Project project = e.getRequiredData(CommonDataKeys.PROJECT);
    if (ChangeListManager.getInstance(project).isFreezedWithNotification("Can not apply patch now")) return;
    FileDocumentManager.getInstance().saveAllDocuments();

    String clipboardText = ClipboardUtil.getTextInClipboard();
    assert clipboardText != null;
    VirtualFile vFile = new LightVirtualFile("clipboardPatchFile", clipboardText);
    new ApplyPatchDifferentiatedDialog(project, new ApplyPatchDefaultExecutor(project), Collections.emptyList(),
                                       ApplyPatchMode.APPLY_PATCH_IN_MEMORY, vFile).show();
  }
}
