// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.javaFX.actions;

import com.intellij.CommonBundle;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.javaFX.JavaFXBundle;
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
    catch (Throwable ex) {
      Messages.showErrorDialog(JavaFXBundle.message("javafx.failed.to.start.scene.builder.error", commandLine.getCommandLineString()), CommonBundle.getErrorTitle());
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

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }
}
