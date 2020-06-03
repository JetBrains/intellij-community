// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.project.impl

import com.intellij.configurationStore.runInAutoSaveDisabledMode
import com.intellij.conversion.ConversionResult
import com.intellij.conversion.ConversionService
import com.intellij.diagnostic.ActivityCategory
import com.intellij.diagnostic.StartUpMeasurer
import com.intellij.diagnostic.runActivity
import com.intellij.featureStatistics.fusCollectors.LifecycleUsageTriggerCollector
import com.intellij.ide.AppLifecycleListener
import com.intellij.ide.GeneralSettings
import com.intellij.ide.IdeEventQueue
import com.intellij.ide.impl.OpenProjectTask
import com.intellij.ide.impl.ProjectUtil
import com.intellij.ide.lightEdit.LightEditCompatible
import com.intellij.ide.startup.impl.StartupManagerImpl
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.runBackgroundableTask
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectBundle
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.project.ex.ProjectManagerEx
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.startup.StartupManager
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.openapi.wm.impl.welcomeScreen.WelcomeFrame
import com.intellij.platform.PlatformProjectOpenProcessor
import com.intellij.projectImport.ProjectAttachProcessor
import com.intellij.projectImport.ProjectOpenedCallback
import com.intellij.serviceContainer.processProjectComponents
import com.intellij.ui.IdeUICustomization
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.ExecutionException
import java.util.concurrent.Future

@ApiStatus.Internal
open class ProjectManagerExImpl : ProjectManagerImpl() {
  override fun loadAndOpenProject(originalFilePath: String): Project? {
    val projectStoreBaseDir = Paths.get(FileUtilRt.toSystemIndependentName(toCanonicalName(originalFilePath)))
    return loadAndOpenProject(projectStoreBaseDir, OpenProjectTask())
  }

  override fun loadAndOpenProject(projectStoreBaseDir: Path, options: OpenProjectTask): Project? {
    return openExistingProject(projectStoreBaseDir, options, this)
  }

  @ApiStatus.Internal
  internal override fun doOpenProject(project: Project): Boolean {
    if (!addToOpened(project)) {
      return false
    }

    val app = ApplicationManager.getApplication()
    if (!app.isUnitTestMode && app.isDispatchThread) {
      LOG.warn("Do not open project in EDT")
    }

    try {
      val future = doLoadProject(project, ProgressManager.getInstance().progressIndicator)
      if (app.isUnitTestMode) {
        waitInTestMode(future)
      }
    }
    catch (e: ProcessCanceledException) {
      app.invokeAndWait { closeProject(project, /* saveProject = */false, /* dispose = */true, /* checkCanClose = */false) }
      notifyProjectOpenFailed()
      return false
    }
    return true
  }
}

private fun openExistingProject(projectStoreBaseDir: Path, options: OpenProjectTask, projectManager: ProjectManagerExImpl): Project? {
  if (options.project != null && projectManager.isProjectOpened(options.project)) {
    return null
  }

  val activity = StartUpMeasurer.startMainActivity("project opening preparation")
  if (!options.forceOpenInNewFrame) {
    val openProjects = projectManager.openProjects
    if (!openProjects.isNullOrEmpty()) {
      var projectToClose = options.projectToClose
      if (projectToClose == null) {
        // if several projects are opened, ask to reuse not last opened project frame, but last focused (to avoid focus switching)
        val lastFocusedFrame = IdeFocusManager.getGlobalInstance().lastFocusedFrame
        projectToClose = lastFocusedFrame?.project
        if (projectToClose == null || projectToClose is LightEditCompatible) {
          projectToClose = openProjects[openProjects.size - 1]
        }
      }
      if (checkExistingProjectOnOpen(projectToClose, options.callback, projectStoreBaseDir)) {
        return null
      }
    }
  }

  var result: PrepareProjectResult? = null
  runInAutoSaveDisabledMode {
    val frameAllocator = if (ApplicationManager.getApplication().isHeadlessEnvironment) ProjectFrameAllocator() else ProjectUiFrameAllocator(options, projectStoreBaseDir)
    val isCompleted = frameAllocator.run {
      activity.end()
      if (options.project == null) {
        result = prepareProject(options, projectStoreBaseDir)
        if (result?.project == null) {
          frameAllocator.projectNotLoaded(error = null)
          return@run
        }
      }
      else {
        result = PrepareProjectResult(options.project, null)
      }

      val project = result!!.project
      frameAllocator.projectLoaded(project)
      if (projectManager.doOpenProject(project)) {
        frameAllocator.projectOpened(project)
      }
      else {
        frameAllocator.projectNotLoaded(error = null)
        result = null
      }
    }

    if (!isCompleted) {
      result = null
    }
  }

  val project = result?.project
  if (project == null) {
    if (options.showWelcomeScreen) {
      WelcomeFrame.showIfNoProjectOpened()
    }
    return null
  }
  if (options.callback != null) {
    options.callback!!.projectOpened(project, result?.module ?: ModuleManager.getInstance(project).modules[0])
  }
  return project
}

private fun prepareProject(options: OpenProjectTask, projectStoreBaseDir: Path): PrepareProjectResult? {
  val project: Project?
  if (options.isNewProject) {
    project = ProjectManagerEx.getInstanceEx().newProject(projectStoreBaseDir, ProjectFrameAllocator.getPresentableName(options, projectStoreBaseDir), options)
  }
  else {
    val indicator = ProgressManager.getInstance().progressIndicator
    indicator?.text = IdeUICustomization.getInstance().projectMessage("progress.text.project.checking.configuration")
    project = convertAndLoadProject(projectStoreBaseDir, options)
    indicator?.text = ""
  }

  if (project == null) {
    return null
  }

  if (options.isNewProject || (options.runConfiguratorsIfNoModules && ModuleManager.getInstance(project).modules.isEmpty())) {
    val module = configureNewProject(project, projectStoreBaseDir, options)
    return PrepareProjectResult(project, module)
  }
  else {
    return PrepareProjectResult(project, module = null)
  }
}

private fun configureNewProject(project: Project, projectStoreBaseDir: Path, options: OpenProjectTask): Module? {
  var module: Module? = null
  ApplicationManager.getApplication().invokeAndWait {
    module = PlatformProjectOpenProcessor.runDirectoryProjectConfigurators(projectStoreBaseDir, project, options.isNewProject)
  }

  if (options.isDummyProject) {
    // add content root for chosen (single) file
    ModuleRootModificationUtil.updateModel(module!!) { model ->
      val entries = model.contentEntries
      // remove custom content entry created for temp directory
      if (entries.size == 1) {
        model.removeContentEntry(entries[0])
      }
      model.addContentEntry(VfsUtilCore.pathToUrl((options.contentRoot ?: projectStoreBaseDir).toString()))
    }
  }
  return module
}

private fun checkExistingProjectOnOpen(projectToClose: Project, callback: ProjectOpenedCallback?, projectDir: Path?): Boolean {
  val settings = GeneralSettings.getInstance()
  val isValidProject = projectDir != null && ProjectUtil.isValidProjectPath(projectDir)
  if (projectDir != null && ProjectAttachProcessor.canAttachToProject() &&
      (!isValidProject || settings.confirmOpenNewProject == GeneralSettings.OPEN_PROJECT_ASK)) {
    val exitCode = ProjectUtil.confirmOpenOrAttachProject()
    if (exitCode == -1) {
      return true
    }
    else if (exitCode == GeneralSettings.OPEN_PROJECT_SAME_WINDOW) {
      if (!ProjectManagerEx.getInstanceEx().closeAndDispose(projectToClose)) {
        return true
      }
    }
    else if (exitCode == GeneralSettings.OPEN_PROJECT_SAME_WINDOW_ATTACH) {
      if (PlatformProjectOpenProcessor.attachToProject(projectToClose, projectDir, callback)) {
        return true
      }
    }
    // process all pending events that can interrupt focus flow
    // todo this can be removed after taming the focus beast
    IdeEventQueue.getInstance().flushQueue()
  }
  else {
    val exitCode = ProjectUtil.confirmOpenNewProject(false)
    if (exitCode == GeneralSettings.OPEN_PROJECT_SAME_WINDOW) {
      if (!ProjectManagerEx.getInstanceEx().closeAndDispose(projectToClose)) {
        return true
      }
    }
    else if (exitCode != GeneralSettings.OPEN_PROJECT_NEW_WINDOW) {
      // not in a new window
      return true
    }
  }
  return false
}

private fun convertAndLoadProject(path: Path, options: OpenProjectTask): Project? {
  var conversionResult: ConversionResult? = null

  if (options.runConversionsBeforeOpen) {
    conversionResult = runActivity("project conversion", category = ActivityCategory.MAIN) {
      ConversionService.getInstance().convert(path)
    }
    if (conversionResult.openingIsCanceled()) {
      return null
    }
  }

  val project = ProjectManagerImpl.createProject(path, options.projectName)
  ProjectManagerImpl.initProject(path, project, options.isRefreshVfsNeeded, null, ProgressManager.getInstance().progressIndicator)
  if (conversionResult != null && !conversionResult.conversionNotNeeded()) {
    StartupManager.getInstance(project).registerPostStartupActivity {
      conversionResult.postStartupActivity(project)
    }
  }
  return project
}

private fun doLoadProject(project: Project, indicator: ProgressIndicator?): Future<*> {
  val waitEdtActivity = StartUpMeasurer.startMainActivity("placing calling projectOpened on event queue")
  if (indicator != null) {
    indicator.text = if (ApplicationManager.getApplication().isInternal) "Waiting on event queue..." else ProjectBundle.message("project.preparing.workspace")
    indicator.isIndeterminate = true
  }
  ApplicationManager.getApplication().invokeAndWait {
    waitEdtActivity.end()
    if (indicator != null && ApplicationManager.getApplication().isInternal) {
      indicator.text = "Running project opened tasks..."
    }

    ProjectManagerImpl.LOG.debug("projectOpened")
    LifecycleUsageTriggerCollector.onProjectOpened(project)
    val activity = StartUpMeasurer.startMainActivity("project opened callbacks")
    ApplicationManager.getApplication().messageBus.syncPublisher(ProjectManager.TOPIC).projectOpened(project)
    // https://jetbrains.slack.com/archives/C5E8K7FL4/p1495015043685628
    // projectOpened in the project components is called _after_ message bus event projectOpened for ages
    // old behavior is preserved for now (smooth transition, to not break all), but this order is not logical,
    // because ProjectComponent.projectOpened it is part of project initialization contract, but message bus projectOpened it is just an event
    // (and, so, should be called after project initialization)
    processProjectComponents(project.picoContainer) { component, pluginDescriptor ->
      StartupManagerImpl.runActivity {
        val componentActivity = StartUpMeasurer.startActivity(component.javaClass.name, ActivityCategory.PROJECT_OPEN_HANDLER, pluginDescriptor.pluginId.idString)
        component.projectOpened()
        componentActivity.end()
      }
    }
    activity.end()
    ProjectImpl.ourClassesAreLoaded = true
  }
  return (StartupManager.getInstance(project) as StartupManagerImpl).projectOpened(indicator)
}

private fun notifyProjectOpenFailed() {
  val app = ApplicationManager.getApplication()
  app.messageBus.syncPublisher(AppLifecycleListener.TOPIC).projectOpenFailed()
  if (!app.isUnitTestMode) {
    WelcomeFrame.showIfNoProjectOpened()
  }
}

@Suppress("HardCodedStringLiteral")
private fun waitInTestMode(future: Future<*>) {
  val app = ApplicationManager.getApplication()
  fun wait() {
    try {
      future.get()
    }
    catch (e: ExecutionException) {
      throw e.cause ?: e
    }
  }

  if (app.isDispatchThread) {
    // process event queue during waiting
    // tasks is run as backgroundable to allow `invokeAndWait` inside startup activities
    // note that this is still a blocking call in tests
    runBackgroundableTask("wait in tests") {
      wait()
    }
  }
  else {
    wait()
  }
}

private data class PrepareProjectResult(val project: Project, val module: Module?)