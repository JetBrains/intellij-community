// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.openapi.vcs.checkin;

import com.intellij.codeInsight.actions.ReformatCodeProcessor;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.CheckinProjectPanel;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.VcsConfiguration;
import com.intellij.openapi.vcs.changes.ui.BooleanCommitOption;
import com.intellij.openapi.vcs.ui.RefreshableOnComponent;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.formatter.FormatterUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

public class ReformatBeforeCheckinHandler extends CheckinHandler implements CheckinMetaHandler {
  protected final Project myProject;
  private final CheckinProjectPanel myPanel;

  public ReformatBeforeCheckinHandler(final Project project, final CheckinProjectPanel panel) {
    myProject = project;
    myPanel = panel;
  }

  @Override
  @Nullable
  public RefreshableOnComponent getBeforeCheckinConfigurationPanel() {
    return new BooleanCommitOption(myPanel, VcsBundle.message("checkbox.checkin.options.reformat.code"), true,
                                   () -> getSettings().REFORMAT_BEFORE_PROJECT_COMMIT,
                                   value -> getSettings().REFORMAT_BEFORE_PROJECT_COMMIT = value);
  }

  protected VcsConfiguration getSettings() {
    return VcsConfiguration.getInstance(myProject);
  }

  @Override
  public void runCheckinHandlers(@NotNull final Runnable finishAction) {
    final VcsConfiguration configuration = VcsConfiguration.getInstance(myProject);
    final Collection<VirtualFile> files = myPanel.getVirtualFiles();

    final Runnable performCheckoutAction = () -> {
      FileDocumentManager.getInstance().saveAllDocuments();
      finishAction.run();
    };

    if (configuration.REFORMAT_BEFORE_PROJECT_COMMIT && !DumbService.isDumb(myProject)) {
      new ReformatCodeProcessor(
        myProject, CheckinHandlerUtil.getPsiFiles(myProject, files), FormatterUtil.REFORMAT_BEFORE_COMMIT_COMMAND_NAME, performCheckoutAction, true
      ).run();
    }
    else {
      performCheckoutAction.run();
    }

  }
}
