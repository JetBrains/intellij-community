// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.zmlx.hg4idea.action;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.concurrency.annotations.RequiresEdt;
import org.jetbrains.annotations.Nullable;
import org.zmlx.hg4idea.repo.HgRepository;
import org.zmlx.hg4idea.repo.HgRepositoryManager;
import org.zmlx.hg4idea.util.HgUtil;

public final class HgActionUtil {

  @Nullable
  @RequiresEdt
  public static HgRepository getSelectedRepositoryFromEvent(AnActionEvent e) {
    final DataContext dataContext = e.getDataContext();
    final Project project = CommonDataKeys.PROJECT.getData(dataContext);
    if (project == null) {
      return null;
    }
    VirtualFile file = e.getData(CommonDataKeys.VIRTUAL_FILE);
    HgRepositoryManager repositoryManager = HgUtil.getRepositoryManager(project);
    return file != null ? repositoryManager.getRepositoryForFileQuick(file) : HgUtil.getCurrentRepository(project);
  }
}
