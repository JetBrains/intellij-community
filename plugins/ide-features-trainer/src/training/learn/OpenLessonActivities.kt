// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package training.learn

import com.intellij.ide.scratch.ScratchFileService
import com.intellij.ide.scratch.ScratchRootType
import com.intellij.ide.startup.StartupManagerEx
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.fileEditor.TextEditorWithPreview
import com.intellij.openapi.progress.runBackgroundableTask
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.startup.StartupManager
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.Computable
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.ToolWindowAnchor
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.ToolWindowType
import com.intellij.openapi.wm.ex.ToolWindowManagerListener
import com.intellij.util.Alarm
import com.intellij.util.concurrency.annotations.RequiresEdt
import training.dsl.LessonUtil
import training.dsl.impl.LessonContextImpl
import training.dsl.impl.LessonExecutor
import training.lang.LangManager
import training.lang.LangSupport
import training.learn.course.KLesson
import training.learn.course.Lesson
import training.learn.course.LessonType
import training.learn.exceptons.LessonPreparationException
import training.learn.lesson.LessonManager
import training.project.ProjectUtils
import training.statistic.LessonStartingWay
import training.statistic.StatisticBase
import training.statistic.StatisticLessonListener
import training.ui.LearnToolWindowFactory
import training.ui.LearningUiManager
import training.util.findLanguageByID
import training.util.getLearnToolWindowForProject
import training.util.isLearningProject
import training.util.learningToolWindow
import java.io.IOException

internal class OpenLessonParameters(val projectWhereToStartLesson: Project,
                                    val lesson: Lesson,
                                    val forceStartLesson: Boolean,
                                    val startingWay: LessonStartingWay)

internal object OpenLessonActivities {
  private val LOG = logger<OpenLessonActivities>()

  @RequiresEdt
  fun openLesson(params: OpenLessonParameters) {
    val projectWhereToStartLesson = params.projectWhereToStartLesson
    LOG.debug("${projectWhereToStartLesson.name}: start openLesson method")

    // Stop the current lesson (if any)
    LessonManager.instance.stopLesson()

    val activeToolWindow = LearningUiManager.activeToolWindow
                           ?: getLearnToolWindowForProject(projectWhereToStartLesson).also {
                             LearningUiManager.activeToolWindow = it
                           }

    if (activeToolWindow != null && activeToolWindow.project != projectWhereToStartLesson) {
      // maybe we need to add some confirmation dialog?
      activeToolWindow.setModulesPanel()
    }

    if (!params.forceStartLesson && LessonManager.instance.lessonShouldBeOpenedCompleted(params.lesson)) {
      // TODO: Do not stop lesson in another toolwindow IFT-110
      LearningUiManager.activeToolWindow?.setLearnPanel() ?: error("No active toolwindow in $projectWhereToStartLesson")
      LessonManager.instance.openLessonPassed(params.lesson as KLesson, projectWhereToStartLesson)
      return
    }

    try {
      val langSupport = LangManager.getInstance().getLangSupport() ?: throw Exception("Language for learning plugin is not defined")

      var learnProject = LearningUiManager.learnProject
      if (learnProject != null && !isLearningProject(learnProject, langSupport)) {
        learnProject = null // We are in the project from another course
      }
      LOG.debug("${projectWhereToStartLesson.name}: trying to get cached LearnProject ${learnProject != null}")
      if (learnProject == null) learnProject = findLearnProjectInOpenedProjects(langSupport)
      LOG.debug("${projectWhereToStartLesson.name}: trying to find LearnProject in opened projects ${learnProject != null}")
      if (learnProject != null) LearningUiManager.learnProject = learnProject

      val lessonType = params.lesson.lessonType
      when {
        lessonType == LessonType.SCRATCH -> {
          LOG.debug("${projectWhereToStartLesson.name}: scratch based lesson")
        }
        lessonType == LessonType.USER_PROJECT -> {
          LOG.debug("The lesson opened in user project ${projectWhereToStartLesson.name}")
        }
        learnProject == null || learnProject.isDisposed -> {
          if (!isLearningProject(projectWhereToStartLesson, langSupport)) {
            //1. learnProject == null and current project has different name then initLearnProject and register post startup open lesson
            LOG.debug("${projectWhereToStartLesson.name}: 1. learnProject is null or disposed")
            initLearnProject(projectWhereToStartLesson, null) {
              LOG.debug("${projectWhereToStartLesson.name}: 1. ... LearnProject has been started")
              openLessonWhenLearnProjectStart(OpenLessonParameters(it, params.lesson, params.forceStartLesson, params.startingWay))
              LOG.debug("${projectWhereToStartLesson.name}: 1. ... open lesson when learn project has been started")
            }
            return
          }
          else {
            LOG.debug(
              "${projectWhereToStartLesson.name}: 0. learnProject is null but the current project (${projectWhereToStartLesson.name})" +
              "is LearnProject then just getFileInLearnProject")
            LearningUiManager.learnProject = projectWhereToStartLesson
            learnProject = projectWhereToStartLesson
          }
        }
        learnProject.isOpen && projectWhereToStartLesson != learnProject -> {
          LOG.debug("${projectWhereToStartLesson.name}: 3. LearnProject is opened but not focused. Ask user to focus to LearnProject")
          askSwitchToLearnProjectBack(learnProject, projectWhereToStartLesson)
          return
        }
        learnProject.isOpen && projectWhereToStartLesson == learnProject -> {
          LOG.debug("${projectWhereToStartLesson.name}: 4. LearnProject is the current project")
        }
        else -> {
          throw Exception("Unable to start Learn project")
        }
      }

      if (lessonType.isProject) {
        if (lessonType == LessonType.USER_PROJECT) {
          prepareAndOpenLesson(params, withCleanup = false)
        } else {
          if (projectWhereToStartLesson != learnProject) {
            LOG.error(Exception("Invalid learning project initialization: " +
                                "projectWhereToStartLesson = $projectWhereToStartLesson, learnProject = $learnProject"))
            return
          }
          prepareAndOpenLesson(params)
        }
      }
      else {
        openLessonForPreparedProject(params)
      }
    }
    catch (e: Exception) {
      LOG.error(e)
    }
  }

  private fun prepareAndOpenLesson(params: OpenLessonParameters, withCleanup: Boolean = true) {
    runBackgroundableTask(LearnBundle.message("learn.project.initializing.process"), project = params.projectWhereToStartLesson) l@{
      val project = params.projectWhereToStartLesson
      val lessonToOpen = params.lesson
      try {
        if (withCleanup) {
          LangManager.getInstance().getLangSupport()?.cleanupBeforeLessons(project)
        }
        lessonToOpen.prepare(project)
      }
      catch (e: LessonPreparationException) {
        thisLogger().warn("Error occurred when preparing the lesson ${lessonToOpen.id}", e)
        return@l
      }
      catch (t: Throwable) {
        thisLogger().error("Error occurred when preparing the lesson ${lessonToOpen.id}", t)
        return@l
      }
      invokeLater {
        openLessonForPreparedProject(params)
      }
    }
  }

  private fun openLessonForPreparedProject(params: OpenLessonParameters) {
    val langSupport = LangManager.getInstance().getLangSupport() ?: throw Exception("Language should be defined by now")
    val project = params.projectWhereToStartLesson
    val lesson = params.lesson

    val vf: VirtualFile? = if (lesson.lessonType == LessonType.SCRATCH) {
      LOG.debug("${project.name}: scratch based lesson")
      getScratchFile(project, lesson, langSupport.filename)
    }
    else {
      LOG.debug("${project.name}: 4. LearnProject is the current project")
      getFileInLearnProject(langSupport, lesson)
    }

    if (lesson.lessonType != LessonType.SCRATCH) {
      ProjectUtils.closeAllEditorsInProject(project)
    }

    if (lesson.lessonType != LessonType.SCRATCH || LearningUiManager.learnProject == project) {
      // do not change view environment for scratch lessons in user project
      hideOtherViews(project)
    }
    // We need to ensure that the learning panel is initialized
    if (showLearnPanel(project, lesson.preferredLearnWindowAnchor(project))) {
      openLessonWhenLearnPanelIsReady(params, vf)
    }
    else waitLearningToolwindow(params, vf)
  }

  private fun openLessonWhenLearnPanelIsReady(params: OpenLessonParameters, vf: VirtualFile?) {
    val project = params.projectWhereToStartLesson
    LOG.debug("${project.name}: Add listeners to lesson")
    addStatisticLessonListenerIfNeeded(project, params.lesson)

    //open next lesson if current is passed
    LOG.debug("${project.name}: Set lesson view")
    LearningUiManager.activeToolWindow = getLearnToolWindowForProject(project)?.also {
      it.setLearnPanel()
    }
    LOG.debug("${project.name}: XmlLesson onStart()")
    params.lesson.onStart(params.startingWay)

    //to start any lesson we need to do 4 steps:
    //1. open editor or find editor
    LOG.debug("${project.name}: PREPARING TO START LESSON:")
    LOG.debug("${project.name}: 1. Open or find editor")
    var textEditor: TextEditor? = null
    if (vf != null && FileEditorManager.getInstance(project).isFileOpen(vf)) {
      val editors = FileEditorManager.getInstance(project).getEditors(vf)
      for (fileEditor in editors) {
        if (fileEditor is TextEditor) {
          textEditor = fileEditor
        }
      }
    }
    if (vf != null && textEditor == null) {
      val editors = FileEditorManager.getInstance(project).openFile(vf, true, true)
      for (fileEditor in editors) {
        if (fileEditor is TextEditor) {
          textEditor = fileEditor
        }
      }
      if (textEditor == null) {
        LOG.error("Cannot open editor for $vf")
        if (params.lesson.lessonType == LessonType.SCRATCH) {
          invokeLater {
            runWriteAction {
              vf.delete(this)
            }
          }
        }
      }
    }

    //2. set the focus on this editor
    //FileEditorManager.getInstance(project).setSelectedEditor(vf, TextEditorProvider.getInstance().getEditorTypeId());
    LOG.debug("${project.name}: 2. Set the focus on this editor")
    if (vf != null)
      FileEditorManager.getInstance(project).openEditor(OpenFileDescriptor(project, vf), true)

    //4. Process lesson
    LOG.debug("${project.name}: 4. Process lesson")
    if (params.lesson is KLesson) processDslLesson(params.lesson, textEditor, project, vf)
    else error("Unknown lesson format")
  }

  private fun waitLearningToolwindow(params: OpenLessonParameters, vf: VirtualFile?) {
    val project = params.projectWhereToStartLesson
    val connect = project.messageBus.connect()
    connect.subscribe(ToolWindowManagerListener.TOPIC, object : ToolWindowManagerListener {
      override fun toolWindowsRegistered(ids: MutableList<String>, toolWindowManager: ToolWindowManager) {
        if (ids.contains(LearnToolWindowFactory.LEARN_TOOL_WINDOW)) {
          val toolWindow = toolWindowManager.getToolWindow(LearnToolWindowFactory.LEARN_TOOL_WINDOW)
          if (toolWindow != null) {
            connect.disconnect()
            invokeLater {
              showLearnPanel(project, params.lesson.preferredLearnWindowAnchor(project))
              openLessonWhenLearnPanelIsReady(params, vf)
            }
          }
        }
      }
    })
  }

  private fun processDslLesson(lesson: KLesson, textEditor: TextEditor?, projectWhereToStartLesson: Project, vf: VirtualFile?) {
    val executor = LessonExecutor(lesson, projectWhereToStartLesson, textEditor?.editor, vf)
    val lessonContext = LessonContextImpl(executor)
    LessonManager.instance.initDslLesson(textEditor?.editor, lesson, executor)
    lesson.lessonContent(lessonContext)
    executor.startLesson()
  }

  private fun hideOtherViews(project: Project) {
    ApplicationManager.getApplication().invokeLater {
      LessonUtil.hideStandardToolwindows(project)
    }
  }

  private fun addStatisticLessonListenerIfNeeded(currentProject: Project, lesson: Lesson) {
    val statLessonListener = StatisticLessonListener(currentProject)
    if (!lesson.lessonListeners.any { it is StatisticLessonListener })
      lesson.addLessonListener(statLessonListener)
  }

  private fun openReadme(project: Project) {
    val root = ProjectUtils.getCurrentLearningProjectRoot()
    val readme = root.findFileByRelativePath("README.md") ?: return
    TextEditorWithPreview.openPreviewForFile(project, readme)
  }

  fun openOnboardingFromWelcomeScreen(onboarding: Lesson, selectedSdk: Sdk?) {
    StatisticBase.logLearnProjectOpenedForTheFirstTime(StatisticBase.LearnProjectOpeningWay.ONBOARDING_PROMOTER)
    initLearnProject(null, selectedSdk) { project ->
      StartupManager.getInstance(project).runAfterOpened {
        invokeLater {
          if (onboarding.properties.canStartInDumbMode) {
            CourseManager.instance.openLesson(project, onboarding, LessonStartingWay.ONBOARDING_PROMOTER, true)
          }
          else {
            DumbService.getInstance(project).runWhenSmart {
              CourseManager.instance.openLesson(project, onboarding, LessonStartingWay.ONBOARDING_PROMOTER, true)
            }
          }
        }
      }
    }
  }

  fun openLearnProjectFromWelcomeScreen(selectedSdk: Sdk?) {
    StatisticBase.logLearnProjectOpenedForTheFirstTime(StatisticBase.LearnProjectOpeningWay.LEARN_IDE)
    initLearnProject(null, selectedSdk) { project ->
      StartupManager.getInstance(project).runAfterOpened {
        invokeLater {
          openReadme(project)
          hideOtherViews(project)
          val anchor = LangManager.getInstance().getLangSupport()?.getToolWindowAnchor() ?: ToolWindowAnchor.LEFT
          showLearnPanel(project, anchor)
          CourseManager.instance.unfoldModuleOnInit = null
          // Try to fix PyCharm double startup indexing :(
          val openWhenSmart = {
            showLearnPanel(project, anchor)
            DumbService.getInstance(project).runWhenSmart {
              showLearnPanel(project, anchor)
            }
          }
          Alarm().addRequest(openWhenSmart, 500)
        }
      }
    }
  }

  private fun showLearnPanel(project: Project, preferredAnchor: ToolWindowAnchor): Boolean {
    val learn = learningToolWindow(project) ?: return false
    if (learn.anchor != preferredAnchor && learn.type == ToolWindowType.DOCKED) {
      learn.setAnchor(preferredAnchor, null)
    }
    learn.show()
    return true
  }

  @RequiresEdt
  private fun openLessonWhenLearnProjectStart(params: OpenLessonParameters) {
    if (params.lesson.properties.canStartInDumbMode) {
      prepareAndOpenLesson(params, withCleanup = false)
      return
    }
    val myLearnProject = params.projectWhereToStartLesson
    fun openLesson() {
      val toolWindowManager = ToolWindowManager.getInstance(myLearnProject)
      val learnToolWindow = toolWindowManager.getToolWindow(LearnToolWindowFactory.LEARN_TOOL_WINDOW)
      if (learnToolWindow != null) {
        DumbService.getInstance(myLearnProject).runWhenSmart {
          // Try to fix PyCharm double startup indexing :(
          val openWhenSmart = {
            DumbService.getInstance(myLearnProject).runWhenSmart {
              prepareAndOpenLesson(params, withCleanup = false)
            }
          }
          Alarm().addRequest(openWhenSmart, 500)
        }
      }
    }

    val startupManager = StartupManager.getInstance(myLearnProject)
    if (startupManager is StartupManagerEx && startupManager.postStartupActivityPassed()) {
      openLesson()
    }
    else {
      startupManager.registerPostStartupActivity {
        openLesson()
      }
    }
  }

  @Throws(IOException::class)
  private fun getScratchFile(project: Project, lesson: Lesson, filename: String): VirtualFile {
    val languageId = lesson.languageId ?: error("Scratch lesson ${lesson.id} should define language")
    var vf: VirtualFile? = null
    val languageByID = findLanguageByID(languageId)
    if (CourseManager.instance.mapModuleVirtualFile.containsKey(lesson.module)) {
      vf = CourseManager.instance.mapModuleVirtualFile[lesson.module]
      ScratchFileService.getInstance().scratchesMapping.setMapping(vf, languageByID)
    }
    if (vf == null || !vf.isValid) {
      //while module info is not stored

      //find file if it is existed
      vf = ScratchFileService.getInstance().findFile(ScratchRootType.getInstance(), filename, ScratchFileService.Option.existing_only)
      if (vf != null) {
        FileEditorManager.getInstance(project).closeFile(vf)
        ScratchFileService.getInstance().scratchesMapping.setMapping(vf, languageByID)
      }

      if (vf == null || !vf.isValid) {
        vf = ScratchRootType.getInstance().createScratchFile(project, filename, languageByID, "")
        assert(vf != null)
      }
      CourseManager.instance.registerVirtualFile(lesson.module, vf!!)
    }
    return vf
  }

  private fun askSwitchToLearnProjectBack(learnProject: Project, currentProject: Project) {
    Messages.showInfoMessage(currentProject,
                             LearnBundle.message("dialog.askToSwitchToLearnProject.message", learnProject.name),
                             LearnBundle.message("dialog.askToSwitchToLearnProject.title"))
  }

  private fun getFileInLearnProject(langSupport: LangSupport, lesson: Lesson): VirtualFile? {
    if (!lesson.properties.openFileAtStart) {
      LOG.debug("${lesson.name} does not open any file at the start")
      return null
    }
    val function = object : Computable<VirtualFile> {
      override fun compute(): VirtualFile {
        val learnProject = LearningUiManager.learnProject!!

        val existedFile = lesson.existedFile ?: lesson.module.primaryLanguage?.projectSandboxRelativePath
        val manager = ProjectRootManager.getInstance(learnProject)
        if (existedFile != null) {
          val root = ProjectUtils.getProjectRoot(langSupport)
          val findFileByRelativePath = root.findFileByRelativePath(existedFile)
          if (findFileByRelativePath != null) return findFileByRelativePath
        }

        val fileName = existedFile ?: lesson.fileName

        var lessonVirtualFile: VirtualFile? = null
        var roots = manager.contentSourceRoots
        if (roots.isEmpty()) {
          roots = manager.contentRoots
        }
        for (file in roots) {
          if (file.name == fileName) {
            lessonVirtualFile = file
            break
          }
          else {
            lessonVirtualFile = file.findChild(fileName)
            if (lessonVirtualFile != null) {
              break
            }
          }
        }
        if (lessonVirtualFile == null) {
          lessonVirtualFile = roots[0].createChildData(this, fileName)
        }

        CourseManager.instance.registerVirtualFile(lesson.module, lessonVirtualFile)
        return lessonVirtualFile
      }
    }

    val vf = ApplicationManager.getApplication().runWriteAction(function)
    assert(vf is VirtualFile)
    return vf
  }

  private fun initLearnProject(projectToClose: Project?, selectedSdk: Sdk?, postInitCallback: (learnProject: Project) -> Unit) {
    val langSupport = LangManager.getInstance().getLangSupport() ?: throw Exception("Language for learning plugin is not defined")
    //if projectToClose is open
    findLearnProjectInOpenedProjects(langSupport)?.let {
      postInitCallback(it)
      return
    }

    if (!ApplicationManager.getApplication().isUnitTestMode && projectToClose != null)
      if (!NewLearnProjectUtil.showDialogOpenLearnProject(projectToClose))
        return //if user abort to open lesson in a new Project
    try {
      NewLearnProjectUtil.createLearnProject(projectToClose, langSupport, selectedSdk) { learnProject ->
        try {
          langSupport.applyToProjectAfterConfigure().invoke(learnProject)
        }
        catch (e: Throwable) {
          LOG.error(e)
          LOG.error("The configuration will be retried after 2 seconds")
          Alarm().addRequest({
            langSupport.applyToProjectAfterConfigure().invoke(learnProject)
            finishProjectInitialization(learnProject, postInitCallback)
          }, 2000)
          return@createLearnProject
        }
        finishProjectInitialization(learnProject, postInitCallback)
      }
    }
    catch (e: IOException) {
      LOG.error(e)
    }
  }

  private fun finishProjectInitialization(learnProject: Project, postInitCallback: (learnProject: Project) -> Unit) {
    LearningUiManager.learnProject = learnProject
    runInEdt {
      postInitCallback(learnProject)
    }
  }

  private fun findLearnProjectInOpenedProjects(langSupport: LangSupport): Project? {
    val openProjects = ProjectManager.getInstance().openProjects
    return openProjects.firstOrNull { isLearningProject(it, langSupport) }
  }

}