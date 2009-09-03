package com.intellij.cvsSupport2.actions;

import com.intellij.cvsSupport2.CvsUtil;
import com.intellij.cvsSupport2.actions.actionVisibility.CvsActionVisibility;
import com.intellij.cvsSupport2.actions.cvsContext.CvsContextWrapper;
import com.intellij.cvsSupport2.util.CvsVfsUtil;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.vcs.actions.VcsContext;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;

import java.io.File;

/**
 * author: lesya
 */

public class UnmarkAddedAction extends AnAction{
  private final CvsActionVisibility myVisibility = new CvsActionVisibility();

  public UnmarkAddedAction() {
    myVisibility.canBePerformedOnSeveralFiles();
    myVisibility.shouldNotBePerformedOnDirectory();
    myVisibility.addCondition(ActionOnSelectedElement.FILES_ARE_LOCALLY_ADDED);
  }

  public void update(AnActionEvent e) {
    myVisibility.applyToEvent(e);
  }

  public void actionPerformed(AnActionEvent e) {
    VcsContext context = CvsContextWrapper.createCachedInstance(e);
    final VirtualFile[] selectedFiles = context.getSelectedFiles();
    ProgressManager.getInstance().runProcessWithProgressSynchronously(new Runnable() {
      public void run() {
        ProgressIndicator progressIndicator = ProgressManager.getInstance().getProgressIndicator();
        for (int i = 0; i < selectedFiles.length; i++) {
          File file = CvsVfsUtil.getFileFor(selectedFiles[i]);
          if (progressIndicator != null){
            progressIndicator.setFraction((double)i/(double)selectedFiles.length);
            progressIndicator.setText(file.getAbsolutePath());
          }
          CvsUtil.removeEntryFor(file);
        }
      }
    }, com.intellij.CvsBundle.message("operation.name.undo.add"), true, context.getProject());
    VirtualFileManager.getInstance().refresh(true);
  }
}
