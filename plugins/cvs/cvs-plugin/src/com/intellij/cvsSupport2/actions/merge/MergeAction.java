// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.cvsSupport2.actions.merge;

import com.intellij.cvsSupport2.actions.actionVisibility.CvsActionVisibility;
import com.intellij.cvsSupport2.actions.cvsContext.CvsContext;
import com.intellij.cvsSupport2.actions.cvsContext.CvsContextWrapper;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.AbstractVcsHelper;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.FileStatusManager;
import com.intellij.openapi.vcs.actions.VcsContext;
import com.intellij.openapi.vfs.ReadonlyStatusHandler;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;


public class MergeAction extends AnAction {
  private static final Logger LOG = Logger.getInstance(MergeAction.class);
  private final CvsActionVisibility myVisibility = new CvsActionVisibility();

  public MergeAction() {
    myVisibility.shouldNotBePerformedOnDirectory();
    myVisibility.canBePerformedOnSeveralFiles();
    myVisibility.addCondition(new CvsActionVisibility.Condition() {
      @Override
      public boolean isPerformedOn(CvsContext context) {
        VirtualFile[] files = context.getSelectedFiles();
        for(VirtualFile file: files) {
          FileStatus status = FileStatusManager.getInstance(context.getProject()).getStatus(file);
          if (status != FileStatus.MERGE && status != FileStatus.MERGED_WITH_CONFLICTS) {
            return false;
          }
        }
        return true;
      }
    });
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    try {

      final VcsContext context = CvsContextWrapper.createCachedInstance(e);
      final VirtualFile[] files = context.getSelectedFiles();
      if (files.length == 0) return;
      final Project project = context.getProject();
      final ReadonlyStatusHandler.OperationStatus operationStatus = ReadonlyStatusHandler.getInstance(project).ensureFilesWritable(Arrays.asList(files));
      if (operationStatus.hasReadonlyFiles()) {
        return;
      }
      AbstractVcsHelper.getInstance(project).showMergeDialog(Arrays.asList(files), new CvsMergeProvider());
    }
    catch (Exception e1) {
      LOG.error(e1);
    }
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    myVisibility.applyToEvent(e);
  }

}
