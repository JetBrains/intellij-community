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
import com.intellij.execution.configurations.JavaParameters;
import com.intellij.execution.configurations.PathEnvironmentVariableUtil;
import com.intellij.execution.process.OSProcessHandler;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.javaFX.JavaFxSettings;
import org.jetbrains.plugins.javaFX.JavaFxSettingsConfigurable;
import org.jetbrains.plugins.javaFX.fxml.JavaFxFileTypeFactory;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * User: anna
 * Date: 2/14/13
 */
public class OpenInSceneBuilderAction extends AnAction {
  private static final Logger LOG = Logger.getInstance("#" + OpenInSceneBuilderAction.class.getName());
  public static final String ORACLE = "Oracle";

  @Override
  public void actionPerformed(AnActionEvent e) {
    final VirtualFile virtualFile = e.getData(CommonDataKeys.VIRTUAL_FILE);
    LOG.assertTrue(virtualFile != null);
    final String path = virtualFile.getPath();

    final JavaFxSettings settings = JavaFxSettings.getInstance();
    String pathToSceneBuilder = settings.getPathToSceneBuilder();
    if (StringUtil.isEmptyOrSpaces(settings.getPathToSceneBuilder())){
      final VirtualFile sceneBuilderFile = FileChooser.chooseFile(JavaFxSettingsConfigurable.createSceneBuilderDescriptor(), e.getProject(), getPredefinedPath());
      if (sceneBuilderFile == null) return;

      pathToSceneBuilder = sceneBuilderFile.getPath();
      settings.setPathToSceneBuilder(FileUtil.toSystemIndependentName(pathToSceneBuilder));
    }

    final Project project = getEventProject(e);
    if (project != null && !Registry.is("scene.builder.start.executable", true)) {
      final Module module = ModuleUtilCore.findModuleForFile(virtualFile, project);
      if (module != null) {
        try {
          final JavaParameters javaParameters = new JavaParameters();
          javaParameters.configureByModule(module, JavaParameters.JDK_AND_CLASSES);

          final File sceneBuilderLibsFile;
          if (SystemInfo.isMac) {
            sceneBuilderLibsFile = new File(new File(pathToSceneBuilder, "Contents"), "Java");
          } else if (SystemInfo.isWindows) {
            File sceneBuilderRoot = new File(pathToSceneBuilder);
            File sceneBuilderRootDir = sceneBuilderRoot.getParentFile();
            if (sceneBuilderRootDir == null) {
              final File foundInPath = PathEnvironmentVariableUtil.findInPath(pathToSceneBuilder);
              if (foundInPath != null) {
                sceneBuilderRootDir = foundInPath.getParentFile();
              }
            }
            sceneBuilderRoot = sceneBuilderRootDir != null ? sceneBuilderRootDir.getParentFile() : null;
            if (sceneBuilderRoot != null) {
              final File libFile = new File(sceneBuilderRoot, "lib");
              if (libFile.isDirectory()) {
                sceneBuilderLibsFile = libFile;
              }
              else {
                final File appFile = new File(sceneBuilderRootDir, "app");
                sceneBuilderLibsFile = appFile.isDirectory() ? appFile : null;
              }
            }
            else {
              sceneBuilderLibsFile = null;
            }
          } else {
            sceneBuilderLibsFile = new File(new File(pathToSceneBuilder).getParent(), "app");
          }
          if (sceneBuilderLibsFile != null) {
            final File[] sceneBuilderLibs = sceneBuilderLibsFile.listFiles();
            if (sceneBuilderLibs != null) {
              for (File jarFile : sceneBuilderLibs) {
                javaParameters.getClassPath().add(jarFile.getPath());
              }
              javaParameters.setMainClass("com.oracle.javafx.authoring.Main");
              javaParameters.getProgramParametersList().add(path);

              final OSProcessHandler processHandler = javaParameters.createOSProcessHandler();
              final String commandLine = processHandler.getCommandLine();
              LOG.info("scene builder command line: " + commandLine);
              processHandler.startNotify();
              return;
            }
          }
        }
        catch (Throwable ex) {
          LOG.info(ex);
        }
      }
    }

    if (SystemInfo.isMac) {
      pathToSceneBuilder += "/Contents/MacOS/scenebuilder-launcher.sh";
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

  @Nullable
  private static VirtualFile getPredefinedPath() {
    String path = null;
    if (SystemInfo.isWindows) {
      final String sb11 = File.separator + "JavaFX Scene Builder 1.1" + File.separator + "JavaFX Scene Builder 1.1.exe";
      final String sb10 = File.separator + "JavaFX Scene Builder 1.0" + File.separator + "bin" + File.separator + "scenebuilder.exe";
      final List<String> suspiciousPaths = new ArrayList<String>();
      final String programFiles = "C:\\Program Files";
      fillPaths(programFiles, sb11, sb10, suspiciousPaths);
      fillPaths(programFiles + " (x86)", sb11, sb10, suspiciousPaths);
      final File sb = FileUtil.findFirstThatExist(ArrayUtil.toStringArray(suspiciousPaths));
      if (sb != null) {
        path = sb.getPath();
      }
    }
    else if (SystemInfo.isMac) {
      final File sb = FileUtil.findFirstThatExist("/Applications/JavaFX Scene Builder 1.1.app", 
                                                  "/Applications/JavaFX Scene Builder 1.0.app");
      if (sb != null) {
        path = sb.getPath();
      }
    } 
    else if (SystemInfo.isUnix) {
      path = "/opt/JavaFXSceneBuilder1.1/JavaFXSceneBuilder1.1";
    }

    return path != null ? LocalFileSystem.getInstance().findFileByPath(FileUtil.toSystemIndependentName(path)) : null;
  }

  private static void fillPaths(String programFilesPath, String sb11, String sb10, List<String> suspiciousPaths) {
    suspiciousPaths.add(new File(programFilesPath, ORACLE).getPath() + sb11);
    suspiciousPaths.add(new File(programFilesPath, ORACLE).getPath() + sb10);
  }
}
