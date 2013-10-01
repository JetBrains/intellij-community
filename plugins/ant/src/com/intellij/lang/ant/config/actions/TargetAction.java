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
package com.intellij.lang.ant.config.actions;

import com.intellij.lang.ant.AntBundle;
import com.intellij.lang.ant.config.AntBuildFile;
import com.intellij.lang.ant.config.AntBuildFileBase;
import com.intellij.lang.ant.config.AntBuildListener;
import com.intellij.lang.ant.config.AntConfiguration;
import com.intellij.lang.ant.config.execution.ExecutionHandler;
import com.intellij.lang.ant.config.impl.BuildFileProperty;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.util.ArrayUtil;

import java.util.Collections;

public final class TargetAction extends DumbAwareAction {
  public static final String DEFAULT_TARGET_NAME = AntBundle.message("ant.target.name.default.target");

  private final String myBuildName;
  private final String[] myTargets;
  private final String myDebugString;
  
  public TargetAction(final AntBuildFile buildFile, final String displayName, final String[] targets, final String description) {
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
    DataContext dataContext = e.getDataContext();
    Project project = CommonDataKeys.PROJECT.getData(dataContext);
    if (project == null) return;

    for (final AntBuildFile buildFile : AntConfiguration.getInstance(project).getBuildFiles()) {
      final String name = buildFile.getPresentableName();
      if (name != null && myBuildName.equals(name)) {
        String[] targets = myTargets.length == 1 && DEFAULT_TARGET_NAME.equals(myTargets[0]) ? ArrayUtil.EMPTY_STRING_ARRAY : myTargets;
        ExecutionHandler.runBuild((AntBuildFileBase)buildFile, targets, null, dataContext, Collections.<BuildFileProperty>emptyList(), AntBuildListener.NULL);
        return;
      }
    }
  }
}