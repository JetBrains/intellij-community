// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.checkin;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.actions.RearrangeCodeProcessor;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.CheckinProjectPanel;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.VcsConfiguration;
import com.intellij.openapi.vcs.changes.ui.BooleanCommitOption;
import com.intellij.openapi.vcs.ui.RefreshableOnComponent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class RearrangeBeforeCheckinHandler extends CheckinHandler implements CheckinMetaHandler {
  public static final String COMMAND_NAME = CodeInsightBundle.message("process.rearrange.code.before.commit");

  private final Project myProject;
  private final CheckinProjectPanel myPanel;

  public RearrangeBeforeCheckinHandler(@NotNull Project project, @NotNull CheckinProjectPanel panel) {
    myProject = project;
    myPanel = panel;
  }

  @Override
  @Nullable
  public RefreshableOnComponent getBeforeCheckinConfigurationPanel() {
    return new BooleanCommitOption(myPanel, VcsBundle.message("checkbox.checkin.options.rearrange.code"), true,
                                   () -> getSettings().REARRANGE_BEFORE_PROJECT_COMMIT,
                                   value -> getSettings().REARRANGE_BEFORE_PROJECT_COMMIT = value);
  }

  private VcsConfiguration getSettings() {
    return VcsConfiguration.getInstance(myProject);
  }

  @Override
  public void runCheckinHandlers(@NotNull final Runnable finishAction) {
    final Runnable performCheckoutAction = () -> {
      FileDocumentManager.getInstance().saveAllDocuments();
      finishAction.run();
    };

    if (VcsConfiguration.getInstance(myProject).REARRANGE_BEFORE_PROJECT_COMMIT && !DumbService.isDumb(myProject)) {
      new RearrangeCodeProcessor(myProject, CheckinHandlerUtil.getPsiFiles(myProject, myPanel.getVirtualFiles()), COMMAND_NAME,
                                 performCheckoutAction, true).run();
    }
    else {
      performCheckoutAction.run();
    }
  }
}
