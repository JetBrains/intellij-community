// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.openapi.vcs.checkin;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.actions.OptimizeImportsProcessor;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.CheckinProjectPanel;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.VcsConfiguration;
import com.intellij.openapi.vcs.changes.ui.BooleanCommitOption;
import com.intellij.openapi.vcs.ui.RefreshableOnComponent;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

public class OptimizeImportsBeforeCheckinHandler extends CheckinHandler implements CheckinMetaHandler {

  public static final String COMMAND_NAME = CodeInsightBundle.message("process.optimize.imports.before.commit");
  
  protected final Project myProject;
  private final CheckinProjectPanel myPanel;

  public OptimizeImportsBeforeCheckinHandler(final Project project, final CheckinProjectPanel panel) {
    myProject = project;
    myPanel = panel;
  }

  @Override
  @Nullable
  public RefreshableOnComponent getBeforeCheckinConfigurationPanel() {
    return new BooleanCommitOption(myPanel, VcsBundle.message("checkbox.checkin.options.optimize.imports"), true,
                                   () -> getSettings().OPTIMIZE_IMPORTS_BEFORE_PROJECT_COMMIT,
                                   value -> getSettings().OPTIMIZE_IMPORTS_BEFORE_PROJECT_COMMIT = value);
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

    if (configuration.OPTIMIZE_IMPORTS_BEFORE_PROJECT_COMMIT && !DumbService.isDumb(myProject)) {
      new OptimizeImportsProcessor(myProject, CheckinHandlerUtil.getPsiFiles(myProject, files), COMMAND_NAME, performCheckoutAction).run();
    }  else {
      finishAction.run();
    }

  }
}
