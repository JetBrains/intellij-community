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
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.progress.runBackgroundableTask
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.startup.StartupManager
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.Computable
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.ToolWindowManager
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
import training.learn.lesson.LessonManager
import training.project.ProjectUtils
import training.statistic.StatisticBase
import training.statistic.StatisticLessonListener
import training.ui.LearnToolWindowFactory
import training.ui.LearningUiManager
import training.util.findLanguageByID
import training.util.isLearningProject
import java.io.IOException

internal object OpenLessonActivities {
  private val LOG = logger<OpenLessonActivities>()

  @RequiresEdt
  fun openLesson(projectWhereToStartLesson: Project, lesson: Lesson) {
    LOG.debug("${projectWhereToStartLesson.name}: start openLesson method")

    // Stop the current lesson (if any)
    LessonManager.instance.stopLesson()

    val activeToolWindow = LearningUiManager.activeToolWindow
                           ?: LearnToolWindowFactory.learnWindowPerProject[projectWhereToStartLesson].also {
                             LearningUiManager.activeToolWindow = it
                           }

    if (activeToolWindow != null && activeToolWindow.project != projectWhereToStartLesson) {
      // maybe we need to add some confirmation dialog?
      activeToolWindow.setModulesPanel()
    }

    if (LessonManager.instance.lessonShouldBeOpenedCompleted(lesson)) {
      // TODO: Do not stop lesson in another toolwindow IFT-110
      LearningUiManager.activeToolWindow?.setLearnPanel() ?: error("No active toolwindow in $projectWhereToStartLesson")
      LessonManager.instance.openLessonPassed(lesson as KLesson, projectWhereToStartLesson)
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

      when {
        lesson.lessonType == LessonType.SCRATCH -> {
          LOG.debug("${projectWhereToStartLesson.name}: scratch based lesson")
        }
        learnProject == null || learnProject.isDisposed -> {
          if (!isLearningProject(projectWhereToStartLesson, langSupport)) {
            //1. learnProject == null and current project has different name then initLearnProject and register post startup open lesson
            LOG.debug("${projectWhereToStartLesson.name}: 1. learnProject is null or disposed")
            initLearnProject(projectWhereToStartLesson) {
              LOG.debug("${projectWhereToStartLesson.name}: 1. ... LearnProject has been started")
              openLessonWhenLearnProjectStart(lesson, it)
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

      if (lesson.lessonType.isProject) {
        if (projectWhereToStartLesson != learnProject) {
          LOG.error(Exception("Invalid learning project initialization: projectWhereToStartLesson = $projectWhereToStartLesson, learnProject = $learnProject"))
          return
        }
        cleanupAndOpenLesson(projectWhereToStartLesson, lesson)
      }
      else {
        openLessonForPreparedProject(projectWhereToStartLesson, lesson)
      }
    }
    catch (e: Exception) {
      LOG.error(e)
    }
  }

  private fun cleanupAndOpenLesson(project: Project, lessonToOpen: Lesson) {
    val lessons = CourseManager.instance.lessonsForModules.filter { it.lessonType == LessonType.PROJECT }
    runBackgroundableTask(LearnBundle.message("learn.project.initializing.process"), project = project) {
      LangManager.getInstance().getLangSupport()?.cleanupBeforeLessons(project)

      for (lesson in lessons) {
        lesson.cleanup(project)
      }

      invokeLater {
        openLessonForPreparedProject(project, lessonToOpen)
      }
    }
  }

  private fun openLessonForPreparedProject(project: Project, lesson: Lesson) {
    val langSupport = LangManager.getInstance().getLangSupport() ?: throw Exception("Language should be defined by now")

    val vf: VirtualFile? = if (lesson.lessonType == LessonType.SCRATCH) {
      LOG.debug("${project.name}: scratch based lesson")
      getScratchFile(project, lesson, langSupport.filename)
    }
    else {
      LOG.debug("${project.name}: 4. LearnProject is the current project")
      getFileInLearnProject(lesson)
    }

    if (lesson.lessonType != LessonType.SCRATCH) {
      ProjectUtils.closeAllEditorsInProject(project)
    }

    if (lesson.lessonType != LessonType.SCRATCH || LearningUiManager.learnProject == project) {
      // do not change view environment for scratch lessons in user project
      hideOtherViews(project)
    }
    // We need to ensure that the learning panel is initialized
    showLearnPanel(project)

    LOG.debug("${project.name}: Add listeners to lesson")
    addStatisticLessonListenerIfNeeded(project, lesson)

    //open next lesson if current is passed
    LOG.debug("${project.name}: Set lesson view")
    LearningUiManager.activeToolWindow = LearnToolWindowFactory.learnWindowPerProject[project]?.also {
      it.setLearnPanel()
    }
    LOG.debug("${project.name}: XmlLesson onStart()")
    lesson.onStart()

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
        if (lesson.lessonType == LessonType.SCRATCH) {
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
    if (lesson is KLesson) processDslLesson(lesson, textEditor, project, vf)
    else error("Unknown lesson format")
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
    val manager = ProjectRootManager.getInstance(project)
    val root = manager.contentRoots[0]
    val readme = root?.findFileByRelativePath("README.md") ?: return
    FileEditorManager.getInstance(project).openFile(readme, true, true)
  }

  fun openOnboardingFromWelcomeScreen(onboarding: Lesson) {
    StatisticBase.logLearnProjectOpenedForTheFirstTime(StatisticBase.LearnProjectOpeningWay.ONBOARDING_PROMOTER)
    initLearnProject(null) { project ->
      StartupManager.getInstance(project).runAfterOpened {
        invokeLater {
          if (onboarding.properties.canStartInDumbMode) {
            CourseManager.instance.openLesson(project, onboarding)
          }
          else {
            DumbService.getInstance(project).runWhenSmart {
              CourseManager.instance.openLesson(project, onboarding)
            }
          }
        }
      }
    }
  }

  fun openLearnProjectFromWelcomeScreen() {
    StatisticBase.logLearnProjectOpenedForTheFirstTime(StatisticBase.LearnProjectOpeningWay.LEARN_IDE)
    initLearnProject(null) { project ->
      StartupManager.getInstance(project).runAfterOpened {
        invokeLater {
          openReadme(project)
          hideOtherViews(project)
          showLearnPanel(project)
          CourseManager.instance.unfoldModuleOnInit = null
          // Try to fix PyCharm double startup indexing :(
          val openWhenSmart = {
            showLearnPanel(project)
            DumbService.getInstance(project).runWhenSmart {
              showLearnPanel(project)
            }
          }
          Alarm().addRequest(openWhenSmart, 500)
        }
      }
    }
  }

  private fun showLearnPanel(project: Project) {
    ToolWindowManager.getInstance(project).getToolWindow(LearnToolWindowFactory.LEARN_TOOL_WINDOW)?.show()
  }

  @RequiresEdt
  private fun openLessonWhenLearnProjectStart(lesson: Lesson, myLearnProject: Project) {
    if (lesson.properties.canStartInDumbMode) {
      openLessonForPreparedProject(myLearnProject, lesson)
      return
    }
    fun openLesson() {
      val toolWindowManager = ToolWindowManager.getInstance(myLearnProject)
      val learnToolWindow = toolWindowManager.getToolWindow(LearnToolWindowFactory.LEARN_TOOL_WINDOW)
      if (learnToolWindow != null) {
        DumbService.getInstance(myLearnProject).runWhenSmart {
          // Try to fix PyCharm double startup indexing :(
          val openWhenSmart = {
            DumbService.getInstance(myLearnProject).runWhenSmart {
              openLessonForPreparedProject(myLearnProject, lesson)
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
    var vf: VirtualFile? = null
    val languageByID = findLanguageByID(lesson.languageId)
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

  @Throws(IOException::class)
  private fun getFileInLearnProject(lesson: Lesson): VirtualFile? {
    if (!lesson.properties.openFileAtStart) {
      LOG.debug("${lesson.name} does not open any file at the start")
      return null
    }
    val function = object : Computable<VirtualFile> {
      override fun compute(): VirtualFile {
        val learnProject = LearningUiManager.learnProject!!

        val existedFile = lesson.existedFile ?: lesson.module.primaryLanguage.projectSandboxRelativePath
        val manager = ProjectRootManager.getInstance(learnProject)
        if (existedFile != null) {
          val root = manager.contentRoots[0]
          val findFileByRelativePath = root?.findFileByRelativePath(existedFile)
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

  private fun initLearnProject(projectToClose: Project?, postInitCallback: (learnProject: Project) -> Unit) {
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
      NewLearnProjectUtil.createLearnProject(projectToClose, langSupport) { learnProject ->
        langSupport.applyToProjectAfterConfigure().invoke(learnProject)
        LearningUiManager.learnProject = learnProject
        runInEdt {
          postInitCallback(learnProject)
        }
      }
    }
    catch (e: IOException) {
      LOG.error(e)
    }
  }

  private fun findLearnProjectInOpenedProjects(langSupport: LangSupport): Project? {
    val openProjects = ProjectManager.getInstance().openProjects
    return openProjects.firstOrNull { isLearningProject(it, langSupport) }
  }

}