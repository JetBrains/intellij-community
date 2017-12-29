/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
import com.intellij.ide.IdeEventQueue;
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
import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.impl.welcomeScreen.WelcomeFrame;
import com.intellij.projectImport.ProjectAttachProcessor;
import com.intellij.projectImport.ProjectOpenProcessor;
import com.intellij.projectImport.ProjectOpenedCallback;
import com.intellij.util.io.PathKt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.EnumSet;

/**
 * @author max
 */
public class PlatformProjectOpenProcessor extends ProjectOpenProcessor {
  private static final Logger LOG = Logger.getInstance("#com.intellij.platform.PlatformProjectOpenProcessor");

  public enum Option {
    FORCE_NEW_FRAME, REOPEN, TEMP_PROJECT
  }

  public static PlatformProjectOpenProcessor getInstance() {
    PlatformProjectOpenProcessor projectOpenProcessor = getInstanceIfItExists();
    assert projectOpenProcessor != null;
    return projectOpenProcessor;
  }

  @Nullable
  public static PlatformProjectOpenProcessor getInstanceIfItExists() {
    ProjectOpenProcessor[] processors = Extensions.getExtensions(EXTENSION_POINT_NAME);
    for (ProjectOpenProcessor processor : processors) {
      if (processor instanceof PlatformProjectOpenProcessor) {
        return (PlatformProjectOpenProcessor)processor;
      }
    }
    return null;
  }

  @Override
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
  @Override
  public Project doOpenProject(@NotNull VirtualFile virtualFile, @Nullable Project projectToClose, boolean forceOpenInNewFrame) {
    EnumSet<Option> options = EnumSet.noneOf(Option.class);
    if (forceOpenInNewFrame) options.add(Option.FORCE_NEW_FRAME);
    return doOpenProject(virtualFile, projectToClose, -1, null, options);
  }

  @Nullable
  public Project doOpenProject(@NotNull VirtualFile file, @Nullable Project projectToClose, int line, @NotNull EnumSet<Option> options) {
    return doOpenProject(file, projectToClose, line, null, options);
  }

  /** @deprecated use {@link #doOpenProject(VirtualFile, Project, int, ProjectOpenedCallback, EnumSet)} (to be removed in IDEA 2019) */
  public static Project doOpenProject(@NotNull VirtualFile virtualFile,
                                      Project projectToClose,
                                      boolean forceOpenInNewFrame,
                                      int line,
                                      @Nullable ProjectOpenedCallback callback,
                                      boolean isReopen) {
    EnumSet<Option> options = EnumSet.noneOf(Option.class);
    if (forceOpenInNewFrame) options.add(Option.FORCE_NEW_FRAME);
    if (isReopen) options.add(Option.REOPEN);
    return doOpenProject(virtualFile, projectToClose, line, callback, options);
  }

  @Nullable
  public static Project doOpenProject(@NotNull VirtualFile virtualFile,
                                      @Nullable Project projectToClose,
                                      int line,
                                      @Nullable ProjectOpenedCallback callback,
                                      @NotNull EnumSet<Option> options) {
    VirtualFile baseDir = virtualFile;
    boolean dummyProject = false;
    String dummyProjectName = null;
    boolean forceOpenInNewFrame = options.contains(Option.FORCE_NEW_FRAME);
    boolean isReopen = options.contains(Option.REOPEN);
    boolean tempProject = options.contains(Option.TEMP_PROJECT);

    if (!baseDir.isDirectory()) {
      if (tempProject) {
        baseDir = null;
      }
      else {
        baseDir = virtualFile.getParent();
        while (baseDir != null && !com.intellij.openapi.project.ProjectUtil.isProjectDirectoryExistsUsingIo(baseDir)) {
          baseDir = baseDir.getParent();
        }
      }
      if (baseDir == null) { // no reasonable directory -> create new temp one or use parent
        if (tempProject || Registry.is("ide.open.file.in.temp.project.dir")) {
          try {
            dummyProjectName = virtualFile.getName();
            File directory = FileUtil.createTempDirectory(dummyProjectName, null, true);
            baseDir = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(directory);
            dummyProject = true;
          }
          catch (IOException ex) {
            LOG.error(ex);
          }
        }
        if (baseDir == null) {
          baseDir = virtualFile.getParent();
        }
      }
    }

    final Path projectDir = Paths.get(FileUtil.toSystemDependentName(baseDir.getPath()), Project.DIRECTORY_STORE_FOLDER);

    Project[] openProjects = ProjectManager.getInstance().getOpenProjects();
    if (!forceOpenInNewFrame && openProjects.length > 0) {
      if (projectToClose == null) {
        projectToClose = openProjects[openProjects.length - 1];
      }

      if (ProjectAttachProcessor.canAttachToProject() && GeneralSettings.getInstance().getConfirmOpenNewProject() == GeneralSettings.OPEN_PROJECT_ASK) {
        final OpenOrAttachDialog dialog = new OpenOrAttachDialog(projectToClose, isReopen, isReopen ? "Reopen Project" : "Open Project");
        if (!dialog.showAndGet()) {
          return null;
        }
        if (dialog.isReplace()) {
          if (!ProjectUtil.closeAndDispose(projectToClose)) {
            return null;
          }
        }
        else if (dialog.isAttach()) {
          if (attachToProject(projectToClose, Paths.get(FileUtil.toSystemDependentName(baseDir.getPath())), callback)) {
            return null;
          }
        }
        // process all pending events that can interrupt focus flow
        // todo this can be removed after taming the focus beast
        IdeEventQueue.getInstance().flushQueue();
      }
      else {
        int exitCode = ProjectUtil.confirmOpenNewProject(false);
        if (exitCode == GeneralSettings.OPEN_PROJECT_SAME_WINDOW) {
          if (!ProjectUtil.closeAndDispose(projectToClose)) {
            return null;
          }
        }
        else if (exitCode != GeneralSettings.OPEN_PROJECT_NEW_WINDOW) {
          // not in a new window
          return null;
        }
      }
    }

    boolean runConfigurators = true;
    boolean newProject = false;
    ProjectManagerEx projectManager = ProjectManagerEx.getInstanceEx();
    Project project = null;
    if (PathKt.exists(projectDir)) {
      try {
        File baseDirIo = VfsUtilCore.virtualToIoFile(baseDir);
        for (ProjectOpenProcessor processor : ProjectOpenProcessor.EXTENSION_POINT_NAME.getExtensions()) {
          processor.refreshProjectFiles(baseDirIo);
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
      PathKt.createDirectories(projectDir);
      project = projectManager.newProject(dummyProject ? dummyProjectName : baseDir.getName(), baseDir.getPath(), true, dummyProject);

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

    if (!virtualFile.isDirectory()) {
      openFileFromCommandLine(project, virtualFile, line);
    }

    if (!projectManager.openProject(project)) {
      WelcomeFrame.showIfNoProjectOpened();
      return null;
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

  public static boolean attachToProject(Project project, @NotNull Path projectDir, ProjectOpenedCallback callback) {
    for (ProjectAttachProcessor processor : Extensions.getExtensions(ProjectAttachProcessor.EP_NAME)) {
      if (processor.attachToProject(project, projectDir, callback)) {
        return true;
      }
    }
    return false;
  }

  private static void openFileFromCommandLine(Project project, VirtualFile file, int line) {
    StartupManager.getInstance(project).registerPostStartupActivity(
      (DumbAwareRunnable)() -> ApplicationManager.getApplication().invokeLater(() -> {
        if (!project.isDisposed() && file.isValid()) {
          (line > 0 ? new OpenFileDescriptor(project, file, line - 1, 0) : new OpenFileDescriptor(project, file)).navigate(true);
        }
      }, ModalityState.NON_MODAL));
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