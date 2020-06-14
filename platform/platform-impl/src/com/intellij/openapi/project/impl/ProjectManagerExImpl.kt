// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.project.impl

import com.intellij.configurationStore.runInAutoSaveDisabledMode
import com.intellij.conversion.ConversionResult
import com.intellij.conversion.ConversionService
import com.intellij.diagnostic.ActivityCategory
import com.intellij.diagnostic.StartUpMeasurer
import com.intellij.diagnostic.runMainActivity
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
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectBundle
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.startup.StartupManager
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.openapi.wm.impl.welcomeScreen.WelcomeFrame
import com.intellij.platform.PlatformProjectOpenProcessor
import com.intellij.projectImport.ProjectAttachProcessor
import com.intellij.projectImport.ProjectOpenedCallback
import com.intellij.serviceContainer.processProjectComponents
import com.intellij.ui.IdeUICustomization
import org.jetbrains.annotations.ApiStatus
import java.awt.event.InvocationEvent
import java.nio.file.Path
import java.nio.file.Paths

@ApiStatus.Internal
open class ProjectManagerExImpl : ProjectManagerImpl() {
  final override fun createProject(name: String?, path: String): Project? {
    return newProject(Paths.get(toCanonicalName(path)),
                      OpenProjectTask(isNewProject = true, runConfigurators = false).withProjectName(name))
  }

  final override fun newProject(projectName: String?, path: String, useDefaultProjectAsTemplate: Boolean, isDummy: Boolean): Project? {
    return newProject(Paths.get(toCanonicalName(path)), OpenProjectTask(isNewProject = true,
                                                                        useDefaultProjectAsTemplate = useDefaultProjectAsTemplate,
                                                                        projectName = projectName))
  }

  final override fun loadAndOpenProject(originalFilePath: String): Project? {
    return openProject(Paths.get(toCanonicalName(originalFilePath)), OpenProjectTask())
  }

  final override fun openProject(projectStoreBaseDir: Path, options: OpenProjectTask): Project? {
    return doOpenProject(projectStoreBaseDir, options, this)
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
      openProject(project, ProgressManager.getInstance().progressIndicator)
    }
    catch (e: ProcessCanceledException) {
      app.invokeAndWait { closeProject(project, /* saveProject = */false, /* dispose = */true, /* checkCanClose = */false) }
      notifyProjectOpenFailed()
      return false
    }
    return true
  }
}

private fun doOpenProject(projectStoreBaseDir: Path, options: OpenProjectTask, projectManager: ProjectManagerExImpl): Project? {
  if (ProjectManagerImpl.LOG.isDebugEnabled && !ApplicationManager.getApplication().isUnitTestMode) {
    ProjectManagerImpl.LOG.debug("open project: ${options}", RuntimeException())
  }

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
      if (checkExistingProjectOnOpen(projectToClose, options.callback, projectStoreBaseDir, projectManager)) {
        return null
      }
    }
  }

  val frameAllocator = if (ApplicationManager.getApplication().isHeadlessEnvironment) ProjectFrameAllocator() else ProjectUiFrameAllocator(
    options, projectStoreBaseDir)
  val result = runInAutoSaveDisabledMode {
    frameAllocator.run {
      activity.end()
      val result: PrepareProjectResult?
      if (options.project == null) {
        result = prepareProject(options, projectStoreBaseDir, projectManager) ?: return@run null
      }
      else {
        result = PrepareProjectResult(options.project, null)
      }

      val project = result.project
      frameAllocator.projectLoaded(project)
      if (projectManager.doOpenProject(project)) {
        frameAllocator.projectOpened(project)
        result
      }
      else {
        null
      }
    }
  }

  if (result == null) {
    frameAllocator.projectNotLoaded(error = null)
    if (options.showWelcomeScreen) {
      WelcomeFrame.showIfNoProjectOpened()
    }
    return null
  }

  val project = result.project
  if (options.callback != null) {
    options.callback!!.projectOpened(project, result.module ?: ModuleManager.getInstance(project).modules[0])
  }
  return project
}

private fun prepareProject(options: OpenProjectTask, projectStoreBaseDir: Path, projectManager: ProjectManagerExImpl): PrepareProjectResult? {
  val project: Project?
  if (options.isNewProject) {
    project = projectManager.newProject(projectStoreBaseDir, options.copy(projectName = ProjectFrameAllocator.getPresentableName(options,
                                                                                                                                 projectStoreBaseDir)))
  }
  else {
    val indicator = ProgressManager.getInstance().progressIndicator
    indicator?.text = IdeUICustomization.getInstance().projectMessage("progress.text.project.checking.configuration")
    project = convertAndLoadProject(projectStoreBaseDir, options)
    indicator?.text = ""
  }

  @Suppress("UNNECESSARY_NOT_NULL_ASSERTION")
  if (project == null || (options.beforeOpen != null && !options.beforeOpen!!(project))) {
    return null
  }

  if (options.runConfigurators && (options.isNewProject || ModuleManager.getInstance(project).modules.isEmpty())) {
    val module = PlatformProjectOpenProcessor.runDirectoryProjectConfigurators(projectStoreBaseDir, project, options.isProjectCreatedWithWizard)
    options.preparedToOpen?.invoke(module)
    return PrepareProjectResult(project, module)
  }
  else {
    return PrepareProjectResult(project, module = null)
  }
}

private fun convertAndLoadProject(path: Path, options: OpenProjectTask): Project? {
  var conversionResult: ConversionResult? = null
  if (options.runConversionBeforeOpen) {
    conversionResult = runMainActivity("project conversion") {
      ConversionService.getInstance().convert(path)
    }
    if (conversionResult.openingIsCanceled()) {
      return null
    }
  }

  val project = ProjectManagerImpl.instantiateProject(path, options.projectName)
  // template as null because convertAndLoadProject method is called only for an existing project
  ProjectManagerImpl.initProject(path, project, options.isRefreshVfsNeeded, null, ProgressManager.getInstance().progressIndicator)
  if (conversionResult != null && !conversionResult.conversionNotNeeded()) {
    StartupManager.getInstance(project).runAfterOpened {
      conversionResult.postStartupActivity(project)
    }
  }
  return project
}

private fun checkExistingProjectOnOpen(projectToClose: Project,
                                       callback: ProjectOpenedCallback?,
                                       projectDir: Path?,
                                       projectManager: ProjectManagerExImpl): Boolean {
  val settings = GeneralSettings.getInstance()
  val isValidProject = projectDir != null && ProjectUtil.isValidProjectPath(projectDir)
  if (projectDir != null && ProjectAttachProcessor.canAttachToProject() &&
      (!isValidProject || settings.confirmOpenNewProject == GeneralSettings.OPEN_PROJECT_ASK)) {
    val exitCode = ProjectUtil.confirmOpenOrAttachProject()
    if (exitCode == -1) {
      return true
    }
    else if (exitCode == GeneralSettings.OPEN_PROJECT_SAME_WINDOW) {
      if (!projectManager.closeAndDispose(projectToClose)) {
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
      if (!projectManager.closeAndDispose(projectToClose)) {
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

private fun openProject(project: Project, indicator: ProgressIndicator?) {
  val waitEdtActivity = StartUpMeasurer.startMainActivity("placing calling projectOpened on event queue")
  if (indicator != null) {
    indicator.text = if (ApplicationManager.getApplication().isInternal) "Waiting on event queue..." else ProjectBundle.message(
      "project.preparing.workspace")
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
        val componentActivity = StartUpMeasurer.startActivity(component.javaClass.name, ActivityCategory.PROJECT_OPEN_HANDLER,
                                                              pluginDescriptor.pluginId.idString)
        component.projectOpened()
        componentActivity.end()
      }
    }
    activity.end()
    ProjectImpl.ourClassesAreLoaded = true
  }

  (StartupManager.getInstance(project) as StartupManagerImpl).projectOpened(indicator)
}

// allow `invokeAndWait` inside startup activities
internal fun waitAndProcessInvocationEventsInIdeEventQueue(startupManager: StartupManagerImpl) {
  val eventQueue = IdeEventQueue.getInstance()
  while (true) {
    // getNextEvent() will block until an event has been posted by another thread, so,
    // peekEvent() is used to check that there is already some event in the queue
    if (eventQueue.peekEvent() == null) {
      if (startupManager.postStartupActivityPassed()) {
        break
      }
      else {
        continue
      }
    }

    val event = eventQueue.nextEvent
    if (event is InvocationEvent) {
      eventQueue.dispatchEvent(event)
    }
  }
}

private fun notifyProjectOpenFailed() {
  val app = ApplicationManager.getApplication()
  app.messageBus.syncPublisher(AppLifecycleListener.TOPIC).projectOpenFailed()
  if (!app.isUnitTestMode) {
    WelcomeFrame.showIfNoProjectOpened()
  }
}

private data class PrepareProjectResult(val project: Project, val module: Module?)