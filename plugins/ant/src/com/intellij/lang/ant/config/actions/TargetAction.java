// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.ant.config.actions;

import com.intellij.lang.ant.AntBundle;
import com.intellij.lang.ant.config.AntBuildFile;
import com.intellij.lang.ant.config.AntBuildFileBase;
import com.intellij.lang.ant.config.AntBuildListener;
import com.intellij.lang.ant.config.AntConfiguration;
import com.intellij.lang.ant.config.execution.ExecutionHandler;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsSafe;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

import static com.intellij.openapi.util.NlsActions.ActionDescription;
import static com.intellij.openapi.util.NlsActions.ActionText;

public final class TargetAction extends DumbAwareAction {
  private final String myBuildName;
  private final List<String> myTargets;
  private final String myDebugString;

  public TargetAction(final AntBuildFile buildFile,
                      final @ActionText String displayName,
                      final List<@NlsSafe String> targets,
                      final @ActionDescription String description) {
    Presentation templatePresentation = getTemplatePresentation();
    templatePresentation.setText(displayName, false);
    templatePresentation.setDescription(description);
    myBuildName = buildFile.getPresentableName();
    myTargets = targets;
    myDebugString = "Target action: " + displayName +
                    "; Build: " + buildFile.getPresentableName() +
                    "; Project: " + buildFile.getProject().getPresentableUrl();
  }

  @Override
  public @NonNls String toString() {
    return myDebugString;
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    Project project = e.getProject();
    if (project == null) return;

    for (final AntBuildFileBase buildFile : AntConfiguration.getInstance(project).getBuildFileList()) {
      final String name = buildFile.getPresentableName();
      if (name != null && myBuildName.equals(name)) {
        final List<String> targets = myTargets.size() == 1 && getDefaultTargetName().equals(myTargets.iterator().next()) ? Collections.emptyList() : myTargets;
        ExecutionHandler.runBuild(buildFile, targets, null, e.getDataContext(), Collections.emptyList(), AntBuildListener.NULL);
        return;
      }
    }
  }


  public static @Nls String getDefaultTargetName() {
    return AntBundle.message("ant.target.name.default.target");
  }
}