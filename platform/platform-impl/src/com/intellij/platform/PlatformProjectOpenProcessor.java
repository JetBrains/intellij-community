// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.platform;

import com.intellij.conversion.CannotConvertException;
import com.intellij.diagnostic.Activity;
import com.intellij.diagnostic.StartUpMeasurer;
import com.intellij.ide.GeneralSettings;
import com.intellij.ide.IdeEventQueue;
import com.intellij.ide.SaveAndSyncHandler;
import com.intellij.ide.impl.OpenProjectTask;
import com.intellij.ide.impl.ProjectUtil;
import com.intellij.ide.lightEdit.LightEditUtil;
import com.intellij.ide.util.PsiNavigationSupport;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
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
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.openapi.wm.IdeFrame;
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

public final class PlatformProjectOpenProcessor extends ProjectOpenProcessor implements CommandLineProjectOpenProcessor {
  private static final Logger LOG = Logger.getInstance(PlatformProjectOpenProcessor.class);

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
      options.useDefaultProjectAsTemplate = false;
    }
    Path baseDir = Paths.get(virtualFile.getPath());
    options.isNewProject = !ProjectUtil.isValidProjectPath(baseDir);
    return doOpenProject(baseDir, options, -1);
  }

  @Override
  @Nullable
  public Project openProjectAndFile(@NotNull VirtualFile virtualFile, int line, boolean tempProject) {
    // force open in a new frame if temp project
    OpenProjectTask options = new OpenProjectTask(/* forceOpenInNewFrame = */ tempProject);
    Path file = Paths.get(virtualFile.getPath());
    if (tempProject) {
      return createTempProjectAndOpenFile(file, options, -1);
    }
    else {
      return doOpenProject(file, options, line);
    }
  }

  /** @deprecated Use {@link #doOpenProject(Path, OpenProjectTask, int)} */
  @Deprecated
  @ApiStatus.ScheduledForRemoval(inVersion = "2021.3")
  public static Project doOpenProject(@NotNull VirtualFile virtualFile,
                                      Project projectToClose,
                                      boolean forceOpenInNewFrame,
                                      int line,
                                      @SuppressWarnings("unused") @Nullable ProjectOpenedCallback callback,
                                      @SuppressWarnings("unused") boolean isReopen) {
    return doOpenProject(Paths.get(virtualFile.getPath()), new OpenProjectTask(forceOpenInNewFrame, projectToClose), line);
  }

  /** @deprecated Use {@link #doOpenProject(Path, OpenProjectTask, int)} */
  @Deprecated
  @ApiStatus.ScheduledForRemoval(inVersion = "2021.3")
  public static Project doOpenProject(@NotNull VirtualFile virtualFile,
                                      @Nullable Project projectToClose,
                                      int line,
                                      @Nullable ProjectOpenedCallback callback,
                                      @NotNull EnumSet<Option> options) {
    OpenProjectTask openProjectOptions = new OpenProjectTask(options.contains(Option.FORCE_NEW_FRAME), projectToClose);
    openProjectOptions.callback = callback;
    return doOpenProject(Paths.get(virtualFile.getPath()), openProjectOptions, line);
  }

  @Nullable
  @ApiStatus.Internal
  public static Project createTempProjectAndOpenFile(@NotNull Path file, @NotNull OpenProjectTask options, int line) {
    if (LightEditUtil.openFile(file)) {
      return LightEditUtil.getProject();
    }
    String dummyProjectName = file.getFileName().toString();
    Path baseDir;
    try {
      baseDir = FileUtil.createTempDirectory(dummyProjectName, null, true).toPath();
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }

    OpenProjectTask copy = options.copy();
    copy.isNewProject = true;
    copy.setDummyProjectName(dummyProjectName);
    Project project = openExistingProject(file, baseDir, copy);
    if (project != null) {
      openFileFromCommandLine(project, file, line);
    }
    return project;
  }

  @Nullable
  @ApiStatus.Internal
  public static Project doOpenProject(@NotNull Path file, @NotNull OpenProjectTask options, int line) {
    Path baseDir = file;
    if (!Files.isDirectory(baseDir)) {
      baseDir = file.getParent();
      while (baseDir != null && !Files.exists(baseDir.resolve(Project.DIRECTORY_STORE_FOLDER))) {
        baseDir = baseDir.getParent();
      }

      // no reasonable directory -> create new temp one or use parent
      if (baseDir == null) {
        if (Registry.is("ide.open.file.in.temp.project.dir")) {
          return createTempProjectAndOpenFile(file, options, line);
        }

        baseDir = file.getParent();
        options.isNewProject = !Files.isDirectory(baseDir.resolve(Project.DIRECTORY_STORE_FOLDER));
      }
    }

    SaveAndSyncHandler saveAndSyncHandler = ApplicationManager.getApplication().getServiceIfCreated(SaveAndSyncHandler.class);
    if (saveAndSyncHandler != null) {
      saveAndSyncHandler.blockSyncOnFrameActivation();
    }
    try {
      Project project = openExistingProject(file, baseDir, options);
      if (project != null && file != baseDir && !Files.isDirectory(file)) {
        openFileFromCommandLine(project, file, line);
      }
      return project;
    }
    finally {
      if (saveAndSyncHandler != null) {
        saveAndSyncHandler.unblockSyncOnFrameActivation();
      }
    }
  }

  @Nullable
  @ApiStatus.Internal
  public static Project openExistingProject(@NotNull Path file,
                                            @Nullable("null for IPR project") Path projectDir,
                                            @NotNull OpenProjectTask options) {
    if (options.getProject() != null && ProjectManagerEx.getInstanceEx().isProjectOpened(options.getProject())) {
      return null;
    }
    Activity activity = StartUpMeasurer.startMainActivity("project opening preparation");
    if (!options.forceOpenInNewFrame) {
      Project[] openProjects = ProjectUtil.getOpenProjects();
      if (openProjects.length > 0) {
        Project projectToClose = options.projectToClose;
        if (projectToClose == null) {
          // if several projects are opened, ask to reuse not last opened project frame, but last focused (to avoid focus switching)
          IdeFrame lastFocusedFrame = IdeFocusManager.getGlobalInstance().getLastFocusedFrame();
          projectToClose = lastFocusedFrame == null ? null : lastFocusedFrame.getProject();
          if (projectToClose == null) {
            projectToClose = openProjects[openProjects.length - 1];
          }
        }

        if (checkExistingProjectOnOpen(projectToClose, options.callback, projectDir)) {
          return null;
        }
      }
    }

    ProjectFrameAllocator frameAllocator = ApplicationManager.getApplication().isHeadlessEnvironment()
                                           ? new ProjectFrameAllocator()
                                           : new ProjectUiFrameAllocator(options, file);
    Ref<Pair<Project, Module>> refResult = new Ref<>(Pair.empty());
    boolean isCompleted = frameAllocator.run(() -> {
      Pair<Project, Module> result;
      Project project = options.getProject();
      if (project == null) {
        CannotConvertException cannotConvertException = null;
        try {
          activity.end();
          result = prepareProject(file, options, projectDir);
        }
        catch (ProcessCanceledException e) {
          throw e;
        }
        catch (CannotConvertException e) {
          LOG.info(e);
          cannotConvertException = e;
          result = Pair.empty();
        }
        catch (Exception e) {
          result = Pair.empty();
          LOG.error(e);
        }

        project = result.first;
        if (project == null) {
          frameAllocator.projectNotLoaded(cannotConvertException);
          return;
        }
      }
      else {
        result = new Pair<>(project, null);
      }

      refResult.set(result);
      frameAllocator.projectLoaded(project);
      if (ProjectManagerEx.getInstanceEx().openProject(project)) {
        frameAllocator.projectOpened(project);
      }
      else {
        refResult.set(Pair.empty());
      }
    });
    if (!isCompleted) {
      refResult.set(Pair.empty());
    }

    Project project = refResult.get().first;
    if (project == null) {
      if (options.showWelcomeScreen) {
        WelcomeFrame.showIfNoProjectOpened();
      }
      return null;
    }

    if (options.callback != null) {
      Module module = refResult.get().second;
      if (module == null) {
        module = ModuleManager.getInstance(project).getModules()[0];
      }
      options.callback.projectOpened(project, module);
    }
    return project;
  }

  @NotNull
  private static Pair<Project, Module> prepareProject(@NotNull Path file,
                                                      @NotNull OpenProjectTask options,
                                                      @NotNull Path baseDir) throws CannotConvertException {
    Project project;
    boolean isNewProject = options.isNewProject;
    if (isNewProject) {
      String projectName = options.getDummyProjectName();
      if (projectName == null) {
        projectName = baseDir.getFileName().toString();
      }
      project = ((ProjectManagerImpl)ProjectManager.getInstance()).newProject(baseDir, projectName, options);
    }
    else {
      ProgressIndicator indicator = ProgressManager.getInstance().getProgressIndicator();
      if (indicator != null) {
        indicator.setText("Checking project configuration...");
      }
      project = ProjectManagerImpl.convertAndLoadProject(baseDir);
      if (indicator != null) {
        indicator.setText("");
      }
    }

    if (project == null) {
      return Pair.empty();
    }

    ProjectBaseDirectory.getInstance(project).setBaseDir(baseDir);

    Module module = configureNewProject(project, baseDir, file, options.getDummyProjectName() == null, isNewProject);

    if (isNewProject) {
      project.save();
    }
    return new Pair<>(project, module);
  }

  private static Module configureNewProject(@NotNull Project project,
                                            @NotNull Path baseDir,
                                            @NotNull Path dummyFileContentRoot,
                                            boolean dummyProject,
                                            boolean newProject) {
    boolean runConfigurators = newProject || ModuleManager.getInstance(project).getModules().length == 0;
    Ref<Module> module = new Ref<>();
    if (runConfigurators) {
      ApplicationManager.getApplication().invokeAndWait(() -> module.set(runDirectoryProjectConfigurators(baseDir, project, newProject)));
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

  private static boolean checkExistingProjectOnOpen(@NotNull Project projectToClose, @Nullable ProjectOpenedCallback callback, @Nullable Path projectDir) {
    if (projectDir != null && ProjectAttachProcessor.canAttachToProject() && GeneralSettings.getInstance().getConfirmOpenNewProject() == GeneralSettings.OPEN_PROJECT_ASK) {
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
        if (attachToProject(projectToClose, projectDir, callback)) {
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
   * @deprecated Use {@link #runDirectoryProjectConfigurators(Path, Project, boolean)}
   */
  @Deprecated
  public static Module runDirectoryProjectConfigurators(@NotNull VirtualFile baseDir, @NotNull Project project) {
    return runDirectoryProjectConfigurators(Paths.get(baseDir.getPath()), project, false);
  }

  public static Module runDirectoryProjectConfigurators(@NotNull Path baseDir, @NotNull Project project, boolean newProject) {
    final Ref<Module> moduleRef = new Ref<>();
    VirtualFile virtualFile = ProjectUtil.getFileAndRefresh(baseDir);
    LOG.assertTrue(virtualFile != null);
    DirectoryProjectConfigurator.EP_NAME.forEachExtensionSafe(configurator -> {
      configurator.configureProject(project, virtualFile, moduleRef, newProject);
    });
    return moduleRef.get();
  }

  public static boolean attachToProject(@NotNull Project project, @NotNull Path projectDir, @Nullable ProjectOpenedCallback callback) {
    return ProjectAttachProcessor.EP_NAME.findFirstSafe(processor -> processor.attachToProject(project, projectDir, callback)) != null;
  }

  private static void openFileFromCommandLine(@NotNull Project project, @NotNull Path file, int line) {
    //noinspection CodeBlock2Expr
    StartupManager.getInstance(project).registerPostStartupActivity((DumbAwareRunnable)() -> {
      ApplicationManager.getApplication().invokeLater(() -> {
        if (project.isDisposed() || !Files.exists(file)) {
          return;
        }

        VirtualFile virtualFile = ProjectUtil.getFileAndRefresh(file);
        if (virtualFile == null) {
          return;
        }

        Navigatable navigatable = line > 0
                                  ? new OpenFileDescriptor(project, virtualFile, line - 1, 0)
                                  : PsiNavigationSupport.getInstance().createNavigatable(project, virtualFile, -1);
        navigatable.navigate(true);
      }, ModalityState.NON_MODAL);
    });
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
