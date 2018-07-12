// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.openapi.vcs.checkin;

import com.intellij.analysis.AnalysisScope;
import com.intellij.codeInspection.ex.GlobalInspectionContextBase;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.CheckinProjectPanel;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.VcsConfiguration;
import com.intellij.openapi.vcs.changes.CommitContext;
import com.intellij.openapi.vcs.changes.ui.BooleanCommitOption;
import com.intellij.openapi.vcs.ui.RefreshableOnComponent;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.util.List;


public class CodeCleanupCheckinHandlerFactory extends CheckinHandlerFactory  {
  @Override
  @NotNull
  public CheckinHandler createHandler(@NotNull final CheckinProjectPanel panel, @NotNull CommitContext commitContext) {
    return new CleanupCodeCheckinHandler(panel);
  }

  private static class CleanupCodeCheckinHandler extends CheckinHandler implements CheckinMetaHandler {
    private final CheckinProjectPanel myPanel;
    private final Project myProject;

    public CleanupCodeCheckinHandler(CheckinProjectPanel panel) {
      myProject = panel.getProject();
      myPanel = panel;
    }

    @Override
    public RefreshableOnComponent getBeforeCheckinConfigurationPanel() {
      return new BooleanCommitOption(myPanel, VcsBundle.message("before.checkin.cleanup.code"), true,
                                     () -> getSettings().CHECK_CODE_CLEANUP_BEFORE_PROJECT_COMMIT,
                                     value -> getSettings().CHECK_CODE_CLEANUP_BEFORE_PROJECT_COMMIT = value);
    }

    @Override
    public void runCheckinHandlers(@NotNull Runnable runnable) {
      if (getSettings().CHECK_CODE_CLEANUP_BEFORE_PROJECT_COMMIT && !DumbService.isDumb(myProject)) {
        List<VirtualFile> filesToProcess = CheckinHandlerUtil.filterOutGeneratedAndExcludedFiles(myPanel.getVirtualFiles(), myProject);
        GlobalInspectionContextBase.modalCodeCleanup(myProject, new AnalysisScope(myProject, filesToProcess), runnable);
      } else {
        runnable.run();
      }
    }

    @NotNull
    private VcsConfiguration getSettings() {
      return VcsConfiguration.getInstance(myProject);
    }
  }
}