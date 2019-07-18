// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.javaFX.fxml.JavaFxFileTypeFactory;
import org.jetbrains.plugins.javaFX.sceneBuilder.SceneBuilderInfo;

import java.io.File;

public class OpenInSceneBuilderAction extends AnAction {
  private static final Logger LOG = Logger.getInstance(OpenInSceneBuilderAction.class);
  public static final String OLD_LAUNCHER = "scenebuilder-launcher.sh";

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
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
  public void update(@NotNull AnActionEvent e) {
    final Presentation presentation = e.getPresentation();
    presentation.setEnabledAndVisible(false);
    final VirtualFile virtualFile = e.getData(CommonDataKeys.VIRTUAL_FILE);
    if (virtualFile != null &&
        JavaFxFileTypeFactory.isFxml(virtualFile) &&
        e.getProject() != null) {
      presentation.setEnabledAndVisible(true);
    }
  }
}
