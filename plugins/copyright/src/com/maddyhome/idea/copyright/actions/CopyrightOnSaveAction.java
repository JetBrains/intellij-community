// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.maddyhome.idea.copyright.actions;

import com.intellij.analysis.AnalysisScope;
import com.intellij.ide.actionsOnSave.impl.ActionsOnSaveFileDocumentManagerListener;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.progress.PerformInBackgroundOption;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;

public final class CopyrightOnSaveAction extends ActionsOnSaveFileDocumentManagerListener.ActionOnSave {
  @Override
  public boolean isEnabledForProject(@NotNull Project project) {
    return CopyrightOnSaveInfoProvider.isUpdateCopyrightOnSaveEnabled(project);
  }

  @Override
  public void processDocuments(@NotNull Project project,
                               @NotNull Document @NotNull [] documents) {
    if (DumbService.isDumb(project)) return;
    FileDocumentManager manager = FileDocumentManager.getInstance();
    VirtualFile[] files = ContainerUtil.mapNotNull(documents, d -> manager.getFile(d)).toArray(VirtualFile.EMPTY_ARRAY);
    if (files.length == 0) {
      return;
    }
    AnalysisScope scope = new AnalysisScope(project, Arrays.asList(files));
    UpdateCopyrightAction.UpdateCopyrightTask task = 
      new UpdateCopyrightAction.UpdateCopyrightTask(project, scope, true, PerformInBackgroundOption.DEAF);
    ProgressManager.getInstance().run(task);
  }
}
