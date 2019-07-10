// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.platform;

import com.intellij.diagnostic.Activity;
import com.intellij.diagnostic.StartUpMeasurer;
import com.intellij.ide.GeneralSettings;
import com.intellij.ide.IdeEventQueue;
import com.intellij.ide.impl.OpenProjectTask;
import com.intellij.ide.impl.ProjectUtil;
import com.intellij.ide.util.PsiNavigationSupport;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.TransactionGuard;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.DumbAwareRunnable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ex.ProjectManagerEx;
import com.intellij.openapi.project.impl.ProjectManagerImpl;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.openapi.wm.impl.IdeFrameImpl;
import com.intellij.openapi.wm.impl.WindowManagerImpl;
import com.intellij.openapi.wm.impl.welcomeScreen.WelcomeFrame;
import com.intellij.pom.Navigatable;
import com.intellij.projectImport.ProjectAttachProcessor;
import com.intellij.projectImport.ProjectOpenProcessor;
import com.intellij.projectImport.ProjectOpenedCallback;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.EnumSet;
import java.util.Objects;

public final class PlatformProjectOpenProcessor extends ProjectOpenProcessor implements CommandLineProjectOpenProcessor {
  private static final Logger LOG = Logger.getInstance("#com.intellij.platform.PlatformProjectOpenProcessor");

  public enum Option {
    FORCE_NEW_FRAME, TEMP_PROJECT
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
    OpenProjectTask options = new OpenProjectTask(forceOpenInNewFrame, projectToClose);
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      // doesn't make sense to use default project in tests for heavy projects
      options.setUseDefaultProjectAsTemplate(false);
    }
    return doOpenProject(Paths.get(virtualFile.getPath()), options, -1);
  }

  @Override
  @Nullable
  public Project openProjectAndFile(@NotNull VirtualFile file, int line, boolean tempProject) {
    OpenProjectTask options = new OpenProjectTask(tempProject);
    if (tempProject) {
      options.setTempProject(true);
    }
    return doOpenProject(Paths.get(file.getPath()), options, line);
  }

  /** @deprecated use {@link #doOpenProject(VirtualFile, Project, int, ProjectOpenedCallback, EnumSet)} (to be removed in IDEA 2019) */
  @ApiStatus.ScheduledForRemoval(inVersion = "2019")
  @Deprecated
  public static Project doOpenProject(@NotNull VirtualFile virtualFile,
                                      Project projectToClose,
                                      boolean forceOpenInNewFrame,
                                      int line,
                                      @Nullable ProjectOpenedCallback callback,
                                      @SuppressWarnings("unused") boolean isReopen) {
    OpenProjectTask openProjectOptions = new OpenProjectTask(forceOpenInNewFrame, projectToClose);
    openProjectOptions.setCallback(callback);
    return doOpenProject(Paths.get(virtualFile.getPath()), openProjectOptions, line);
  }

  /**
   * @deprecated Use Path instead of VirtualFile.
   */
  @Nullable
  @Deprecated
  public static Project doOpenProject(@NotNull VirtualFile virtualFile,
                                      @Nullable Project projectToClose,
                                      int line,
                                      @Nullable ProjectOpenedCallback callback,
                                      @NotNull EnumSet<Option> options) {
    OpenProjectTask openProjectOptions = new OpenProjectTask(options.contains(Option.FORCE_NEW_FRAME), projectToClose);
    openProjectOptions.setCallback(callback);
    return doOpenProject(Paths.get(virtualFile.getPath()), openProjectOptions, line);
  }

  @Nullable
  public static Project doOpenProject(@NotNull Path file, @NotNull OpenProjectTask options, int line) {
    String dummyProjectName = null;
    Path baseDir = file;
    if (!Files.isDirectory(baseDir)) {
      if (options.isTempProject()) {
        baseDir = null;
      }
      else {
        baseDir = file.getParent();
        while (baseDir != null && !Files.exists(baseDir)) {
          baseDir = baseDir.getParent();
        }
      }

      // no reasonable directory -> create new temp one or use parent
      if (baseDir == null) {
        if (options.isTempProject() || Registry.is("ide.open.file.in.temp.project.dir")) {
          try {
            dummyProjectName = file.getFileName().toString();
            baseDir = FileUtil.createTempDirectory(dummyProjectName, null, true).toPath();
          }
          catch (IOException ex) {
            LOG.error(ex);
          }
        }

        if (baseDir == null) {
          baseDir = file.getParent();
        }
      }
    }

    if (!options.getForceOpenInNewFrame()) {
      Project[] openProjects = ProjectManager.getInstance().getOpenProjects();
      if (openProjects.length > 0) {
        Project projectToClose = options.getProjectToClose();
        if (projectToClose == null) {
          projectToClose = ProjectUtil.getProjectToClose(openProjects);
        }

        if (checkExistingProjectOnOpen(projectToClose, options.getCallback(), baseDir)) {
          return null;
        }
      }
    }

    return openExistingDirectoryBasedProjectInANewFrame(file, baseDir, options, line, dummyProjectName);
  }

  /**
   * "new frame" also means reusing of closed project frame (that's closed by {@link #doOpenProject}) for example.
   */
  @Nullable
  @ApiStatus.Internal
  public static Project openExistingDirectoryBasedProjectInANewFrame(@NotNull Path file,
                                                                     @NotNull Path baseDir,
                                                                     @NotNull OpenProjectTask options,
                                                                     int line,
                                                                     @Nullable String dummyProjectName) {
    Pair<Project, Module> result;
    if (ApplicationManager.getApplication().isHeadlessEnvironment()) {
      result = prepareAndOpenProject(file, options, baseDir, dummyProjectName);
    }
    else {
      IdeFrameImpl frame = showFrame(options);
      Ref<Pair<Project, Module>> refResult = Ref.create();
      Runnable process = () -> refResult.set(prepareAndOpenProject(file, options, baseDir, dummyProjectName));
      TransactionGuard.getInstance().submitTransactionAndWait(() -> {
        boolean progressCompleted = ProgressManager
          .getInstance().runProcessWithProgressSynchronously(process, "Loading Project...", true, null, frame.getComponent());
        if (!progressCompleted) {
          refResult.set(null);
        }
      });
      result = refResult.get();
    }

    if (result == null || result.first == null) {
      WelcomeFrame.showIfNoProjectOpened();
      return null;
    }

    if (file != baseDir && !Files.isDirectory(file)) {
      openFileFromCommandLine(result.first, file, line);
    }

    if (options.getCallback() != null) {
      options.getCallback().projectOpened(result.first, result.second);
    }
    return result.first;
  }

  @NotNull
  private static IdeFrameImpl showFrame(@NotNull OpenProjectTask options) {
    WindowManagerImpl windowManager = (WindowManagerImpl)WindowManager.getInstance();
    IdeFrameImpl freeRootFrame = windowManager.getRootFrame();
    if (freeRootFrame != null) {
      return freeRootFrame;
    }

    Activity showFrameActivity = StartUpMeasurer.start("show frame");
    IdeFrameImpl frame = windowManager.showFrame(options);
    showFrameActivity.end();
    // runProcessWithProgressSynchronously still processes EDT events
    ApplicationManager.getApplication().invokeLater(() -> {
      Activity activity = StartUpMeasurer.start("init frame");
      if (frame.isDisplayable()) {
        frame.init();
      }
      activity.end();
    }, ModalityState.any());
    return frame;
  }

  @Nullable
  private static Pair<Project, Module> prepareAndOpenProject(@NotNull Path file,
                                                             @NotNull OpenProjectTask options,
                                                             @NotNull Path baseDir,
                                                             @Nullable String dummyProjectName) {
    boolean newProject = false;
    ProjectManagerEx projectManager = ProjectManagerEx.getInstanceEx();
    Project project;
    Path dotIdeaDir = baseDir.resolve(Project.DIRECTORY_STORE_FOLDER);
    if (Files.isDirectory(dotIdeaDir)) {
      project = tryLoadProject(baseDir);
    }
    else {
      project = ((ProjectManagerImpl)projectManager).newProject(baseDir, dummyProjectName == null ? baseDir.getFileName().toString() : dummyProjectName, !options.isUseDefaultProjectAsTemplate(), true);
      newProject = true;
    }

    if (project == null) {
      return null;
    }

    ProjectBaseDirectory.getInstance(project).setBaseDir(baseDir);

    Module module = configureNewProject(project, baseDir, file, dummyProjectName == null, newProject);

    if (newProject) {
      project.save();
    }
    return projectManager.openProject(project) ? new Pair<>(project, module) : null;
  }

  @Nullable
  private static Project tryLoadProject(@NotNull Path baseDir) {
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

  private static Module configureNewProject(@NotNull Project project,
                                            @NotNull Path baseDir,
                                            @NotNull Path dummyFileContentRoot,
                                            boolean dummyProject,
                                            boolean newProject) {
    boolean runConfigurators = newProject || ModuleManager.getInstance(project).getModules().length == 0;
    final Ref<Module> module = new Ref<>();
    if (runConfigurators) {
      ApplicationManager.getApplication().invokeAndWait(() -> module.set(runDirectoryProjectConfigurators(baseDir, project)));
    }
    else {
      module.set(ModuleManager.getInstance(project).getModules()[0]);
    }

    if (runConfigurators && dummyProject) {
      // add content root for chosen (single) file
      ModuleRootModificationUtil.updateModel(module.get(), model -> {
        ContentEntry[] entries = model.getContentEntries();
        // remove custom content entry created for temp directory
        if (entries.length == 1) {
          model.removeContentEntry(entries[0]);
        }
        model.addContentEntry(VfsUtilCore.pathToUrl(dummyFileContentRoot.toString()));
      });
    }
    return module.get();
  }

  private static boolean checkExistingProjectOnOpen(@NotNull Project projectToClose, @Nullable ProjectOpenedCallback callback, Path baseDir) {
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
        if (attachToProject(projectToClose, baseDir, callback)) {
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

  /**
   * @deprecated Use {@link #runDirectoryProjectConfigurators(Path, Project)}
   */
  @Deprecated
  public static Module runDirectoryProjectConfigurators(@NotNull VirtualFile baseDir, @NotNull Project project) {
    return runDirectoryProjectConfigurators(Paths.get(baseDir.getPath()), project);
  }

  public static Module runDirectoryProjectConfigurators(@NotNull Path baseDir, @NotNull Project project) {
    final Ref<Module> moduleRef = new Ref<>();
    VirtualFile virtualFile = getFileAndRefresh(baseDir);
    for (DirectoryProjectConfigurator configurator: DirectoryProjectConfigurator.EP_NAME.getIterable()) {
      try {
        configurator.configureProject(project, virtualFile, moduleRef);
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

  private static void openFileFromCommandLine(@NotNull Project project, @NotNull Path file, int line) {
    //noinspection CodeBlock2Expr
    StartupManager.getInstance(project).registerPostStartupActivity((DumbAwareRunnable)() -> {
      ApplicationManager.getApplication().invokeLater(() -> {
          if (!project.isDisposed() && Files.exists(file)) {
            VirtualFile virtualFile = getFileAndRefresh(file);
            Navigatable navigatable = line > 0
                                      ? new OpenFileDescriptor(project, virtualFile, line - 1, 0)
                                      : PsiNavigationSupport.getInstance().createNavigatable(project, virtualFile, -1);
            navigatable.navigate(true);
          }
        }, ModalityState.NON_MODAL);
    });
  }

  @NotNull
  private static VirtualFile getFileAndRefresh(Path file) {
    VirtualFile virtualFile = Objects.requireNonNull(LocalFileSystem.getInstance().refreshAndFindFileByPath(FileUtil.toSystemIndependentName(file.toString())));
    virtualFile.refresh(false, false);
    return virtualFile;
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