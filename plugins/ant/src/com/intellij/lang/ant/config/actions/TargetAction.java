/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

import java.util.Collections;
import java.util.List;

public final class TargetAction extends DumbAwareAction {
  public static final String DEFAULT_TARGET_NAME = AntBundle.message("ant.target.name.default.target");

  private final String myBuildName;
  private final List<String> myTargets;
  private final String myDebugString;
  
  public TargetAction(final AntBuildFile buildFile, final String displayName, final List<String> targets, final String description) {
    Presentation templatePresentation = getTemplatePresentation();
    templatePresentation.setText(displayName, false);
    templatePresentation.setDescription(description);
    myBuildName = buildFile.getPresentableName();
    myTargets = targets;
    myDebugString = "Target action: " + displayName +
                    "; Build: " + buildFile.getPresentableName() +
                    "; Project: " + buildFile.getProject().getPresentableUrl();
  }

  public String toString() {
    return myDebugString;
  }

  public void actionPerformed(AnActionEvent e) {
    Project project = e.getProject();
    if (project == null) return;

    for (final AntBuildFile buildFile : AntConfiguration.getInstance(project).getBuildFileList()) {
      final String name = buildFile.getPresentableName();
      if (name != null && myBuildName.equals(name)) {
        final List<String> targets = myTargets.size() == 1 && DEFAULT_TARGET_NAME.equals(myTargets.iterator().next()) ? Collections.emptyList() : myTargets;
        ExecutionHandler.runBuild((AntBuildFileBase)buildFile, targets, null, e.getDataContext(), Collections.emptyList(), AntBuildListener.NULL);
        return;
      }
    }
  }
}