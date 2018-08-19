/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.cvsSupport2.actions;

import com.intellij.CvsBundle;
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
import org.jetbrains.annotations.NotNull;

import java.io.File;

/**
 * @author lesya
 */
public class UnmarkAddedAction extends AnAction{
  private final CvsActionVisibility myVisibility = new CvsActionVisibility();

  public UnmarkAddedAction() {
    myVisibility.canBePerformedOnSeveralFiles();
    myVisibility.shouldNotBePerformedOnDirectory();
    myVisibility.addCondition(ActionOnSelectedElement.FILES_ARE_LOCALLY_ADDED);
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    myVisibility.applyToEvent(e);
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    VcsContext context = CvsContextWrapper.createCachedInstance(e);
    final VirtualFile[] selectedFiles = context.getSelectedFiles();
    ProgressManager.getInstance().runProcessWithProgressSynchronously(() -> {
      ProgressIndicator progressIndicator = ProgressManager.getInstance().getProgressIndicator();
      for (int i = 0; i < selectedFiles.length; i++) {
        File file = CvsVfsUtil.getFileFor(selectedFiles[i]);
        if (progressIndicator != null){
          progressIndicator.setFraction((double)i/(double)selectedFiles.length);
          progressIndicator.setText(file.getAbsolutePath());
        }
        CvsUtil.removeEntryFor(file);
      }
    }, CvsBundle.message("operation.name.undo.add"), true, context.getProject());
    VirtualFileManager.getInstance().asyncRefresh(null);
  }
}
