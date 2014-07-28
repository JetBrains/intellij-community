/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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

import java.util.Arrays;


public class MergeAction extends AnAction {
  private static final Logger LOG = Logger.getInstance("#com.intellij.cvsSupport2.actions.merge.MergeAction");
  private final CvsActionVisibility myVisibility = new CvsActionVisibility();

  public MergeAction() {
    myVisibility.shouldNotBePerformedOnDirectory();
    myVisibility.canBePerformedOnSeveralFiles();
    myVisibility.addCondition(new CvsActionVisibility.Condition() {
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

  public void actionPerformed(AnActionEvent e) {
    try {

      final VcsContext context = CvsContextWrapper.createCachedInstance(e);
      final VirtualFile[] files = context.getSelectedFiles();
      if (files.length == 0) return;
      final Project project = context.getProject();
      final ReadonlyStatusHandler.OperationStatus operationStatus =
        ReadonlyStatusHandler.getInstance(project).ensureFilesWritable(files);
      if (operationStatus.hasReadonlyFiles()) {
        return;
      }
      AbstractVcsHelper.getInstance(project).showMergeDialog(Arrays.asList(files), new CvsMergeProvider());
    }
    catch (Exception e1) {
      LOG.error(e1);
    }
  }

  public void update(AnActionEvent e) {
    myVisibility.applyToEvent(e);
  }

}
