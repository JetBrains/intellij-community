// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.platform;

import com.intellij.ide.GeneralSettings;
import com.intellij.ide.IdeEventQueue;
import com.intellij.ide.impl.ProjectUtil;
import com.intellij.ide.util.PsiNavigationSupport;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.DumbAwareRunnable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ex.ProjectManagerEx;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.IdeFrame;
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
public class PlatformProjectOpenProcessor extends ProjectOpenProcessor implements CommandLineProjectOpenProcessor {
  private static final Logger LOG = Logger.getInstance("#com.intellij.platform.PlatformProjectOpenProcessor");

  public enum Option {
    FORCE_NEW_FRAME, REOPEN, TEMP_PROJECT, DO_NOT_USE_DEFAULT_PROJECT
  }

  public static PlatformProjectOpenProcessor getInstance() {
    PlatformProjectOpenProcessor projectOpenProcessor = getInstanceIfItExists();
    assert projectOpenProcessor != null;
    return projectOpenProcessor;
  }

  @Nullable
  public static PlatformProjectOpenProcessor getInstanceIfItExists() {
    for (ProjectOpenProcessor processor : EXTENSION_POINT_NAME.getExtensionList()) {
      if (processor instanceof PlatformProjectOpenProcessor) {
        return (PlatformProjectOpenProcessor)processor;
      }
    }
    return null;
  }

  @Override
  public boolean canOpenProject(@NotNull final VirtualFile file) {
    return file.isDirectory();
  }

  @Override
  public boolean isProjectFile(@NotNull VirtualFile file) {
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
    if (forceOpenInNewFrame) {
      options.add(Option.FORCE_NEW_FRAME);
    }
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      // doesn't make sense to use default project in tests for heavy projects
      options.add(Option.DO_NOT_USE_DEFAULT_PROJECT);
    }
    return doOpenProject(virtualFile, projectToClose, -1, null, options);
  }

  @Override
  @Nullable
  public Project openProjectAndFile(@NotNull VirtualFile file, int line, boolean tempProject) {
    EnumSet<PlatformProjectOpenProcessor.Option> options = EnumSet.noneOf(PlatformProjectOpenProcessor.Option.class);
    if (tempProject) {
      options.add(PlatformProjectOpenProcessor.Option.TEMP_PROJECT);
      options.add(PlatformProjectOpenProcessor.Option.FORCE_NEW_FRAME);
    }


    return doOpenProject(file, null, line, null, options);
  }

  /** @deprecated use {@link #doOpenProject(VirtualFile, Project, int, ProjectOpenedCallback, EnumSet)} (to be removed in IDEA 2019) */
  @Deprecated
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
    return doOpenProject(virtualFile, projectToClose, line, callback, options, null);
  }

  @Nullable
  public static Project doOpenProject(@NotNull VirtualFile virtualFile,
                                      @Nullable Project projectToClose,
                                      int line,
                                      @Nullable ProjectOpenedCallback callback,
                                      @NotNull EnumSet<Option> options,
                                      @Nullable IdeFrame frame) {
    VirtualFile baseDir = virtualFile;
    boolean dummyProject = false;
    String dummyProjectName = null;
    boolean forceOpenInNewFrame = options.contains(Option.FORCE_NEW_FRAME);
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
      // no reasonable directory -> create new temp one or use parent
      if (baseDir == null) {
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

    Project[] openProjects = ProjectManager.getInstance().getOpenProjects();
    if (!forceOpenInNewFrame && openProjects.length > 0) {
      if (projectToClose == null) {
        projectToClose = openProjects[openProjects.length - 1];
      }

      if (checkExistingProjectOnOpen(projectToClose, callback, baseDir)) {
        return null;
      }
    }

    Pair<Project, Module> result = null;
    if (frame == null) {
      result = prepareAndOpenProject(virtualFile, options, baseDir, dummyProject, dummyProjectName);
    }
    else {
      Ref<Pair<Project, Module>> refResult = Ref.create();
      VirtualFile finalBaseDir = baseDir;
      boolean finalDummyProject = dummyProject;
      String finalDummyProjectName = dummyProjectName;
      boolean progressCompleted = ProgressManager.getInstance().runProcessWithProgressSynchronously(
        () -> refResult.set(prepareAndOpenProject(virtualFile, options, finalBaseDir, finalDummyProject, finalDummyProjectName)),
        "Loading Project...", true, null, frame.getComponent()
      );
      if (progressCompleted) {
        result = refResult.get();
      }
    }

    if (result == null || result.first == null) {
      WelcomeFrame.showIfNoProjectOpened();
      return null;
    }

    if (!virtualFile.isDirectory()) {
      openFileFromCommandLine(result.first, virtualFile, line);
    }

    if (callback != null) {
      callback.projectOpened(result.first, result.second);
    }

    return result.first;
  }

  @Nullable
  private static Pair<Project, Module> prepareAndOpenProject(@NotNull VirtualFile virtualFile,
                                                             @NotNull EnumSet<Option> options,
                                                             VirtualFile baseDir,
                                                             boolean dummyProject,
                                                             String dummyProjectName) {
    boolean newProject = false;
    ProjectManagerEx projectManager = ProjectManagerEx.getInstanceEx();
    Project project;
    if (PathKt.exists(Paths.get(FileUtil.toSystemDependentName(baseDir.getPath()), Project.DIRECTORY_STORE_FOLDER))) {
      project = tryLoadProject(baseDir);
    }
    else {
      project = projectManager.newProject(dummyProject ? dummyProjectName : baseDir.getName(), baseDir.getPath(), !options.contains(Option.DO_NOT_USE_DEFAULT_PROJECT), dummyProject);
      newProject = true;
    }

    if (project == null) {
      return null;
    }

    ProjectBaseDirectory.getInstance(project).setBaseDir(baseDir);

    Module module = configureNewProject(project, baseDir, virtualFile, dummyProject, newProject);

    if (newProject) {
      project.save();
    }
    return projectManager.openProject(project) ? new Pair<>(project, module) : null;
  }

  private static Project tryLoadProject(VirtualFile baseDir) {
    try {
      for (ProjectOpenProcessor processor : ProjectOpenProcessor.EXTENSION_POINT_NAME.getExtensionList()) {
        processor.refreshProjectFiles(baseDir);
      }
      return ProjectManagerEx.getInstanceEx().convertAndLoadProject(baseDir);
    }
    catch (Exception e) {
      LOG.error(e);
      return null;
    }
  }

  private static Module configureNewProject(Project project, VirtualFile baseDir, @NotNull VirtualFile dummyFileContentRoot,
                                            boolean dummyProject, boolean newProject) {
    boolean runConfigurators = newProject || ModuleManager.getInstance(project).getModules().length == 0;
    Module module = runConfigurators ? runDirectoryProjectConfigurators(baseDir, project) : ModuleManager.getInstance(project).getModules()[0];
    if (runConfigurators && dummyProject) { // add content root for chosen (single) file
      ModuleRootModificationUtil.updateModel(module, model -> {
        ContentEntry[] entries = model.getContentEntries();
        if (entries.length == 1) model.removeContentEntry(entries[0]); // remove custom content entry created for temp directory
        model.addContentEntry(dummyFileContentRoot);
      });
    }
    return module;
  }

  private static boolean checkExistingProjectOnOpen(@NotNull Project projectToClose, @Nullable ProjectOpenedCallback callback, VirtualFile baseDir) {
    if (ProjectAttachProcessor.canAttachToProject() && GeneralSettings.getInstance().getConfirmOpenNewProject() == GeneralSettings.OPEN_PROJECT_ASK) {
      final int exitCode = ProjectUtil.confirmOpenOrAttachProject();
      if (exitCode == -1) {
        return true;
      }
      else if (exitCode == GeneralSettings.OPEN_PROJECT_SAME_WINDOW) {
        if (!ProjectManagerEx.getInstanceEx().closeAndDispose(projectToClose)) {
          return true;
        }
      }
      else if (exitCode == GeneralSettings.OPEN_PROJECT_SAME_WINDOW_ATTACH) {
        if (attachToProject(projectToClose, Paths.get(FileUtil.toSystemDependentName(baseDir.getPath())), callback)) {
          return true;
        }
      }
      // process all pending events that can interrupt focus flow
      // todo this can be removed after taming the focus beast
      IdeEventQueue.getInstance().flushQueue();
    }
    else {
      int exitCode = ProjectUtil.confirmOpenNewProject(false);
      if (exitCode == GeneralSettings.OPEN_PROJECT_SAME_WINDOW) {
        if (!ProjectManagerEx.getInstanceEx().closeAndDispose(projectToClose)) {
          return true;
        }
      }
      else if (exitCode != GeneralSettings.OPEN_PROJECT_NEW_WINDOW) {
        // not in a new window
        return true;
      }
    }
    return false;
  }

  public static Module runDirectoryProjectConfigurators(VirtualFile baseDir, Project project) {
    final Ref<Module> moduleRef = new Ref<>();
    for (DirectoryProjectConfigurator configurator: DirectoryProjectConfigurator.EP_NAME.getExtensionList()) {
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
    for (ProjectAttachProcessor processor : ProjectAttachProcessor.EP_NAME.getExtensionList()) {
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
          (line > 0 ? new OpenFileDescriptor(project, file, line - 1, 0) : PsiNavigationSupport.getInstance().createNavigatable(project, file, -1)).navigate(true);
        }
      }, ModalityState.NON_MODAL));
  }

  @Nullable
  @Override
  public Icon getIcon() {
    return null;
  }

  @NotNull
  @Override
  public String getName() {
    return "text editor";
  }
}