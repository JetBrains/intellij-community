/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package org.jetbrains.plugins.javaFX.actions;

import com.intellij.CommonBundle;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.plugins.javaFX.fxml.JavaFxFileTypeFactory;
import org.jetbrains.plugins.javaFX.sceneBuilder.SceneBuilderInfo;

import java.io.File;

/**
 * User: anna
 * Date: 2/14/13
 */
public class OpenInSceneBuilderAction extends AnAction {
  private static final Logger LOG = Logger.getInstance("#" + OpenInSceneBuilderAction.class.getName());
  public static final String OLD_LAUNCHER = "scenebuilder-launcher.sh";

  @Override
  public void actionPerformed(AnActionEvent e) {
    final VirtualFile virtualFile = e.getData(CommonDataKeys.VIRTUAL_FILE);
    LOG.assertTrue(virtualFile != null);
    final String path = virtualFile.getPath();
    final Project project = getEventProject(e);

    final SceneBuilderInfo info = SceneBuilderInfo.get(project, true);
    if (info == SceneBuilderInfo.EMPTY) {
      return;
    }

    String pathToSceneBuilder = info.path;

    if (SystemInfo.isMac) {
      pathToSceneBuilder += "/Contents/MacOS/"; 
      if (new File(pathToSceneBuilder, OLD_LAUNCHER).exists()) {
        pathToSceneBuilder += OLD_LAUNCHER;
      } else {
        pathToSceneBuilder += "SceneBuilder";
      }
    }

    final GeneralCommandLine commandLine = new GeneralCommandLine();
    try {
      commandLine.setExePath(FileUtil.toSystemDependentName(pathToSceneBuilder));
      commandLine.addParameter(path);
      commandLine.createProcess();
    }
    catch (Exception ex) {
      Messages.showErrorDialog("Failed to start SceneBuilder: " + commandLine.getCommandLineString(), CommonBundle.getErrorTitle());
    }
  }

  @Override
  public void update(AnActionEvent e) {
    final Presentation presentation = e.getPresentation();
    presentation.setEnabled(false);
    presentation.setVisible(false);
    final VirtualFile virtualFile = e.getData(CommonDataKeys.VIRTUAL_FILE);
    if (virtualFile != null &&
        JavaFxFileTypeFactory.isFxml(virtualFile) &&
        e.getProject() != null) {
      presentation.setEnabled(true);
      presentation.setVisible(true);
    }
  }
}
