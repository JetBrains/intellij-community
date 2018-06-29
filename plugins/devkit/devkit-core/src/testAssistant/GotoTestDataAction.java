/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package org.jetbrains.idea.devkit.testAssistant;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.util.ui.FilePathSplittingPolicy;

import javax.swing.*;
import java.io.File;
import java.util.Collections;

class GotoTestDataAction extends AnAction implements Comparable {
  private final String myFilePath;
  private final Project myProject;

  public GotoTestDataAction(String filePath, Project project, Icon icon) {
    super("Go to " + FilePathSplittingPolicy.SPLIT_BY_SEPARATOR.getPresentableName(new File(filePath), 50), null, icon);
    myFilePath = filePath;
    myProject = project;
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    RelativePoint point = JBPopupFactory.getInstance().guessBestPopupLocation(e.getDataContext());
    TestDataNavigationHandler.navigate(point, Collections.singletonList(myFilePath), myProject);
  }

  @Override
  public int compareTo(Object o) {
    return o instanceof GotoTestDataAction ? 0 : 1;
  }
}
