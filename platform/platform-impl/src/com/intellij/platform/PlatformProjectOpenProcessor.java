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
package com.intellij.platform;

import com.intellij.ide.GeneralSettings;
import com.intellij.ide.impl.ProjectUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.DumbAwareRunnable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ex.ProjectManagerEx;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.impl.welcomeScreen.WelcomeFrame;
import com.intellij.projectImport.ProjectAttachProcessor;
import com.intellij.projectImport.ProjectOpenProcessor;
import com.intellij.projectImport.ProjectOpenedCallback;
import com.intellij.util.Consumer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.File;
import java.io.IOException;

/**
 * @author max
 */
public class PlatformProjectOpenProcessor extends ProjectOpenProcessor {
  private static final Logger LOG = Logger.getInstance("#com.intellij.platform.PlatformProjectOpenProcessor");

  public static PlatformProjectOpenProcessor getInstance() {
    PlatformProjectOpenProcessor projectOpenProcessor = getInstanceIfItExists();
    assert projectOpenProcessor != null;
    return projectOpenProcessor;
  }

  @Nullable
  public static PlatformProjectOpenProcessor getInstanceIfItExists() {
    ProjectOpenProcessor[] processors = Extensions.getExtensions(EXTENSION_POINT_NAME);
    for(ProjectOpenProcessor processor: processors) {
      if (processor instanceof PlatformProjectOpenProcessor) {
        return (PlatformProjectOpenProcessor) processor;
      }
    }
    return null;
  }

  public boolean canOpenProject(final VirtualFile file) {
    return file.isDirectory();
  }

  @Override
  public boolean isProjectFile(VirtualFile file) {
    return false;
  }

  @Override
  public boolean lookForProjectsInDirectory() {
    return false;
  }

  @Nullable
  public Project doOpenProject(@NotNull final VirtualFile virtualFile, @Nullable final Project projectToClose, final boolean forceOpenInNewFrame) {
    return doOpenProject(virtualFile, projectToClose, forceOpenInNewFrame, -1, null, false);
  }

  @Nullable
  public static Project doOpenProject(@NotNull final VirtualFile virtualFile,
                                      Project projectToClose,
                                      final boolean forceOpenInNewFrame,
                                      final int line,
                                      @Nullable ProjectOpenedCallback callback,
                                      final boolean isReopen) {
    VirtualFile baseDir = virtualFile;
    boolean dummyProject = false;
    String dummyProjectName = null;

    if (!baseDir.isDirectory()) {
      baseDir = virtualFile.getParent();
      while (baseDir != null) {
        if (new File(FileUtil.toSystemDependentName(baseDir.getPath()), Project.DIRECTORY_STORE_FOLDER).exists()) {
          break;
        }
        baseDir = baseDir.getParent();
      }
      if (baseDir == null) { // no reasonable directory -> create new temp one or use parent
        if (Registry.is("ide.open.file.in.temp.project.dir")) {
          try {
            dummyProjectName = virtualFile.getName();
            File directory = FileUtil.createTempDirectory(dummyProjectName, null, true);
            baseDir = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(directory);
            dummyProject = true;
          } catch (IOException ex) {
            LOG.error(ex);
          }
        }
        if (baseDir == null) baseDir = virtualFile.getParent();
      }
    }

    final File projectDir = new File(FileUtil.toSystemDependentName(baseDir.getPath()), Project.DIRECTORY_STORE_FOLDER);

    Project[] openProjects = ProjectManager.getInstance().getOpenProjects();
    if (!forceOpenInNewFrame && openProjects.length > 0) {
      if (projectToClose == null) {
        projectToClose = openProjects[openProjects.length - 1];
      }

      if (ProjectAttachProcessor.canAttachToProject()) {
        final OpenOrAttachDialog dialog = new OpenOrAttachDialog(projectToClose, isReopen, isReopen ? "Reopen Project" : "Open Project");
        dialog.show();
        if (dialog.getExitCode() != DialogWrapper.OK_EXIT_CODE) {
          return null;
        }
        if (dialog.isReplace()) {
          if (!ProjectUtil.closeAndDispose(projectToClose)) return null;
        }
        else if (dialog.isAttach()) {
          if (attachToProject(projectToClose, projectDir, callback)) return null;
        }
      }
      else {
        int exitCode = ProjectUtil.confirmOpenNewProject(false);
        if (exitCode == GeneralSettings.OPEN_PROJECT_SAME_WINDOW) {
          if (!ProjectUtil.closeAndDispose(projectToClose)) return null;
        }
        else if (exitCode != GeneralSettings.OPEN_PROJECT_NEW_WINDOW) { // not in a new window
          return null;
        }
      }
    }

    boolean runConfigurators = true, newProject = false;
    ProjectManagerEx projectManager = ProjectManagerEx.getInstanceEx();
    Project project = null;

    if (projectDir.exists()) {
      try {
        for (ProjectOpenProcessor processor : ProjectOpenProcessor.EXTENSION_POINT_NAME.getExtensions()) {
          processor.refreshProjectFiles(projectDir);
        }

        project = projectManager.convertAndLoadProject(baseDir.getPath());

        if (project != null) {
          Module[] modules = ModuleManager.getInstance(project).getModules();
          if (modules.length > 0) {
            runConfigurators = false;
          }
        }
      }
      catch (Exception e) {
        LOG.error(e);
      }
    }
    else {
      //noinspection ResultOfMethodCallIgnored
      projectDir.mkdirs();

      String projectName = dummyProject ? dummyProjectName : projectDir.getParentFile().getName();
      project = projectManager.newProject(projectName, projectDir.getParent(), true, dummyProject);

      newProject = true;
    }

    if (project == null) {
      WelcomeFrame.showIfNoProjectOpened();
      return null;
    }

    ProjectBaseDirectory.getInstance(project).setBaseDir(baseDir);

    Module module = runConfigurators ? runDirectoryProjectConfigurators(baseDir, project) : ModuleManager.getInstance(project).getModules()[0];
    if (runConfigurators && dummyProject) { // add content root for chosen (single) file
      ModuleRootModificationUtil.updateModel(module, model -> {
        ContentEntry[] entries = model.getContentEntries();
        if (entries.length == 1) model.removeContentEntry(entries[0]); // remove custom content entry created for temp directory
        model.addContentEntry(virtualFile);
      });
    }

    if (newProject) {
      project.save();
    }

    openFileFromCommandLine(project, virtualFile, line);

    if (!projectManager.openProject(project)) {
      WelcomeFrame.showIfNoProjectOpened();
      final Project finalProject = project;
      ApplicationManager.getApplication().runWriteAction(() -> Disposer.dispose(finalProject));
      return project;
    }

    if (callback != null) {
      callback.projectOpened(project, module);
    }

    return project;
  }

  public static Module runDirectoryProjectConfigurators(VirtualFile baseDir, Project project) {
    final Ref<Module> moduleRef = new Ref<>();
    for (DirectoryProjectConfigurator configurator: Extensions.getExtensions(DirectoryProjectConfigurator.EP_NAME)) {
      try {
        configurator.configureProject(project, baseDir, moduleRef);
      }
      catch (Exception e) {
        LOG.error(e);
      }
    }
    return moduleRef.get();
  }

  private static boolean attachToProject(Project project, File projectDir, ProjectOpenedCallback callback) {
    final ProjectAttachProcessor[] extensions = Extensions.getExtensions(ProjectAttachProcessor.EP_NAME);
    for (ProjectAttachProcessor processor : extensions) {
      if (processor.attachToProject(project, projectDir, callback)) {
        return true;
      }
    }
    return false;
  }

  private static void openFileFromCommandLine(final Project project, final VirtualFile file, final int line) {
    StartupManager.getInstance(project).registerPostStartupActivity(new DumbAwareRunnable() {
      @Override
      public void run() {
        ApplicationManager.getApplication().invokeLater(() -> {
          if (!project.isDisposed() && file.isValid() && !file.isDirectory()) {
            if (line > 0) {
              new OpenFileDescriptor(project, file, line - 1, 0).navigate(true);
            }
            else {
              new OpenFileDescriptor(project, file).navigate(true);
            }
          }
        }, ModalityState.NON_MODAL);
      }
    });
  }

  @Nullable
  @Override
  public Icon getIcon() {
    return null;
  }

  @Override
  public String getName() {
    return "text editor";
  }
}