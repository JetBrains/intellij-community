// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.impl

import com.intellij.ide.GeneralSettings
import com.intellij.ide.trustedProjects.TrustedProjects
import com.intellij.ide.trustedProjects.impl.TrustedProjectStartupDialog
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.VetoableProjectManagerListener
import com.intellij.openapi.project.ex.ProjectManagerEx
import com.intellij.openapi.project.impl.P3Support
import com.intellij.openapi.project.impl.P3SupportInstaller
import com.intellij.projectImport.ProjectAttachProcessor
import com.intellij.projectImport.ProjectOpenedCallback
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.IndexingTestUtil
import com.intellij.testFramework.closeProjectAsync
import com.intellij.testFramework.junit5.SystemProperty
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import com.intellij.testFramework.useProjectAsync
import com.intellij.testFramework.withProjectAsync
import com.intellij.util.ThreeState
import com.intellij.util.asDisposable
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.EnumSource
import org.junit.jupiter.params.provider.ValueSource
import java.nio.file.Path
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.coroutines.cancellation.CancellationException

@TestApplication
@SystemProperty("idea.trust.headless.disabled", "false")
class TrustedProjectsTest {

  private val testRoot by tempPathFixture()

  @Test
  fun `prefer closest ancestor to determine the trusted state`() {
    val projects = Path.of("projects/")
    val outerDir = Path.of("projects/outer")
    val innerDir = Path.of("projects/outer/inner")

    TrustedProjects.setProjectTrusted(projects, true)
    Assertions.assertTrue(TrustedProjects.isProjectTrusted(innerDir))
    TrustedProjects.setProjectTrusted(outerDir, false)
    Assertions.assertFalse(TrustedProjects.isProjectTrusted(innerDir))
    TrustedProjects.setProjectTrusted(innerDir, true)
    Assertions.assertTrue(TrustedProjects.isProjectTrusted(innerDir))
  }

  @Test
  fun `return unsure if there are no information about ancestors`() {
    val projectRoot1 = Path.of("project/root1/")
    val projectRoot2 = Path.of("project/root2/")
    TrustedProjects.setProjectTrusted(projectRoot1, true)
    Assertions.assertEquals(ThreeState.YES, TrustedProjects.getProjectTrustedState(projectRoot1))
    Assertions.assertEquals(ThreeState.UNSURE, TrustedProjects.getProjectTrustedState(projectRoot2))
  }

  // IJPL-253268: Used from JBC in IjLight
  @Test
  fun `do not offer to trust the location of a project stored in the IDE config directory`() {

    Assertions.assertTrue(TrustedProjects.isProjectLocationOfferedForTrust(testRoot.resolve("project")))

    val mirroredProjectDir = PathManager.getOriginalConfigDir().resolve("projects").resolve("0123456789abcdef")
    Assertions.assertFalse(TrustedProjects.isProjectLocationOfferedForTrust(mirroredProjectDir))

    Assertions.assertFalse(TrustedProjects.isProjectLocationOfferedForTrust(testRoot.root!!))
  }

  @Test
  fun `test trusted project state after ProjectManager#newProject`(): Unit = runBlocking {
    val projectRoot = testRoot.resolve("project")
    val openProjectTask = OpenProjectTask {
      projectName = "project"
    }
    ProjectManagerEx.getInstanceEx()
      .newProjectAsync(projectRoot, openProjectTask)
      .awaitInitialisation()
      .useProjectAsync { project ->
        Assertions.assertTrue(TrustedProjects.isProjectTrusted(project))
      }
  }

  @ParameterizedTest
  @CsvSource(
    "true, TRUST_AND_OPEN, YES",
    "false, TRUST_AND_OPEN, YES",
    "true, OPEN_IN_SAFE_MODE, NO",
    "false, OPEN_IN_SAFE_MODE, NO",
    "true, CANCEL, UNSURE",
    "false, CANCEL, UNSURE"
  )
  fun `test trusted project state after ProjectManager#openProject`(
    isNewProject: Boolean,
    openChoice: OpenUntrustedProjectChoice,
    expectedTrustedState: ThreeState,
  ): Unit = runBlocking {
    val projectRoot = testRoot.resolve("project")

    TrustedProjectStartupDialog.setDialogChoiceInTests(openChoice, asDisposable())

    Assertions.assertEquals(ThreeState.UNSURE, TrustedProjects.getProjectTrustedState(projectRoot))

    val openProjectTask = OpenProjectTask {
      this.projectName = "project"
      this.isNewProject = isNewProject
    }

    when (openChoice) {
      OpenUntrustedProjectChoice.TRUST_AND_OPEN, OpenUntrustedProjectChoice.OPEN_IN_SAFE_MODE -> {
        ProjectManagerEx.getInstanceEx()
          .openProjectAsync(projectRoot, openProjectTask)!!
          .awaitInitialisation()
          .useProjectAsync { project ->
            Assertions.assertEquals(expectedTrustedState, TrustedProjects.getProjectTrustedState(project))
          }
      }
      OpenUntrustedProjectChoice.CANCEL -> {
        runCatching {
          ProjectManagerEx.getInstanceEx()
            .openProjectAsync(projectRoot, openProjectTask)!!
        }.onSuccess { project ->
          project.awaitInitialisation()
          project.closeProjectAsync()
          Assertions.fail<Nothing> {
            "The trusted project dialog was closed with the cancel choice. " +
            "Therefore the project open operation should be cancelled."
          }
        }.onFailure { exception ->
          Assertions.assertInstanceOf(CancellationException::class.java, exception)
        }
      }
    }

    Assertions.assertEquals(expectedTrustedState, TrustedProjects.getProjectTrustedState(projectRoot))
  }

  /**
   * The ways to open a project. Each one goes through a different branch of `ProjectManagerImpl.openProjectAsync`.
   */
  enum class OpenMode {
    NO_OPEN_PROJECT,
    FORCE_NEW_FRAME,
    FORCE_REUSE_FRAME,
    SAME_WINDOW,
    NEW_WINDOW,
  }

  @ParameterizedTest
  @EnumSource(OpenMode::class)
  fun `cancel in the trust dialog stops the project opening`(mode: OpenMode): Unit = runBlocking {
    val projectRoot = testRoot.resolve("project")
    TrustedProjectStartupDialog.setDialogChoiceInTests(OpenUntrustedProjectChoice.CANCEL, asDisposable())

    withProjectToClose(mode) { projectToClose ->
      runCatching {
        ProjectManagerEx.getInstanceEx().openProjectAsync(projectRoot, createOpenProjectTask(mode, projectToClose))
      }.onSuccess { project ->
        project?.closeProjectAsync()
        Assertions.fail<Nothing> { "The trust dialog was not shown or its cancel choice was ignored in the $mode mode" }
      }.onFailure { exception ->
        Assertions.assertInstanceOf(CancellationException::class.java, exception)
      }

      // IJPL-256774: the trust dialog must be shown before the current project is closed
      if (projectToClose != null) {
        Assertions.assertTrue(ProjectManagerEx.getInstanceEx().isProjectOpened(projectToClose))
      }
    }

    Assertions.assertEquals(ThreeState.UNSURE, TrustedProjects.getProjectTrustedState(projectRoot))
  }

  @ParameterizedTest
  @EnumSource(OpenMode::class)
  fun `trust in the trust dialog opens a trusted project`(mode: OpenMode): Unit = runBlocking {
    val projectRoot = testRoot.resolve("project")
    TrustedProjectStartupDialog.setDialogChoiceInTests(OpenUntrustedProjectChoice.TRUST_AND_OPEN, asDisposable())

    withProjectToClose(mode) { projectToClose ->
      openProjectAndCheckTrustedState(projectRoot, createOpenProjectTask(mode, projectToClose), ThreeState.YES)
    }

    Assertions.assertEquals(ThreeState.YES, TrustedProjects.getProjectTrustedState(projectRoot))
  }

  @ParameterizedTest
  @CsvSource(
    "NO_OPEN_PROJECT, true",
    "FORCE_NEW_FRAME, true",
    "FORCE_REUSE_FRAME, true",
    "SAME_WINDOW, true",
    "NEW_WINDOW, true",
    "NO_OPEN_PROJECT, false",
    "FORCE_NEW_FRAME, false",
    "FORCE_REUSE_FRAME, false",
    "SAME_WINDOW, false",
    "NEW_WINDOW, false",
  )
  fun `known trusted state skips the trust dialog`(mode: OpenMode, isTrusted: Boolean): Unit = runBlocking {
    val projectRoot = testRoot.resolve("project")
    TrustedProjects.setProjectTrusted(projectRoot, isTrusted)
    // the project opens only if the trust dialog is not shown
    TrustedProjectStartupDialog.setDialogChoiceInTests(OpenUntrustedProjectChoice.CANCEL, asDisposable())
    val expectedTrustedState = if (isTrusted) ThreeState.YES else ThreeState.NO

    withProjectToClose(mode) { projectToClose ->
      openProjectAndCheckTrustedState(projectRoot, createOpenProjectTask(mode, projectToClose), expectedTrustedState)
    }

    Assertions.assertEquals(expectedTrustedState, TrustedProjects.getProjectTrustedState(projectRoot))
  }

  @ParameterizedTest
  @EnumSource(OpenMode::class, names = ["FORCE_REUSE_FRAME", "SAME_WINDOW"])
  fun `vetoed close of the current project stops the project opening`(mode: OpenMode): Unit = runBlocking {
    val projectRoot = testRoot.resolve("project")
    TrustedProjectStartupDialog.setDialogChoiceInTests(OpenUntrustedProjectChoice.TRUST_AND_OPEN, asDisposable())

    withProjectToClose(mode) { projectToClose ->
      withVetoedClose(projectToClose!!) {
        Assertions.assertNull(ProjectManagerEx.getInstanceEx().openProjectAsync(projectRoot, createOpenProjectTask(mode, projectToClose)))
      }
      Assertions.assertEquals(listOf(projectToClose), ProjectManagerEx.getInstanceEx().openProjects.toList())
    }

    // the current behavior: the trust dialog comes before the close, so the trust choice is saved
    Assertions.assertEquals(ThreeState.YES, TrustedProjects.getProjectTrustedState(projectRoot))
  }

  @Test
  fun `project for a child process opens there without the trust dialog in the parent process`(): Unit = runBlocking {
    val projectRoot = testRoot.resolve("project")
    // the open fails with CancellationException if the trust dialog is shown in this process
    TrustedProjectStartupDialog.setDialogChoiceInTests(OpenUntrustedProjectChoice.CANCEL, asDisposable())
    val p3Support = RecordingP3Support { it != projectRoot }

    withP3Support(p3Support) {
      withProjectToClose(OpenMode.NEW_WINDOW) { projectToClose ->
        Assertions.assertNull(ProjectManagerEx.getInstanceEx().openProjectAsync(projectRoot, createOpenProjectTask(OpenMode.NEW_WINDOW, projectToClose)))
        Assertions.assertEquals(listOf(projectToClose), ProjectManagerEx.getInstanceEx().openProjects.toList())
      }
    }

    Assertions.assertEquals(listOf(projectRoot), p3Support.childProcessProjectRoots)
    Assertions.assertEquals(ThreeState.UNSURE, TrustedProjects.getProjectTrustedState(projectRoot))
  }

  @Test
  fun `child process shows the trust dialog for its own project`(): Unit = runBlocking {
    val projectRoot = testRoot.resolve("project")
    TrustedProjectStartupDialog.setDialogChoiceInTests(OpenUntrustedProjectChoice.CANCEL, asDisposable())
    // the child process opens only its own project, and no other project is open in it
    val p3Support = RecordingP3Support { it == projectRoot }

    withP3Support(p3Support) {
      runCatching {
        ProjectManagerEx.getInstanceEx().openProjectAsync(projectRoot, OpenProjectTask { projectName = "project" })
      }.onSuccess { project ->
        project?.closeProjectAsync()
        Assertions.fail<Nothing> { "The child process did not show the trust dialog for its own project" }
      }.onFailure { exception ->
        Assertions.assertInstanceOf(CancellationException::class.java, exception)
      }
    }

    Assertions.assertEquals(emptyList<Path>(), p3Support.childProcessProjectRoots)
    Assertions.assertEquals(ThreeState.UNSURE, TrustedProjects.getProjectTrustedState(projectRoot))
  }

  @Test
  fun `vetoed close of the current project does not start a child process`(): Unit = runBlocking {
    val projectRoot = testRoot.resolve("project")
    // the open fails with CancellationException if the trust dialog is shown in this process
    TrustedProjectStartupDialog.setDialogChoiceInTests(OpenUntrustedProjectChoice.CANCEL, asDisposable())
    val p3Support = RecordingP3Support { it != projectRoot }

    withP3Support(p3Support) {
      withProjectToClose(OpenMode.SAME_WINDOW) { projectToClose ->
        withVetoedClose(projectToClose!!) {
          Assertions.assertNull(ProjectManagerEx.getInstanceEx().openProjectAsync(projectRoot, createOpenProjectTask(OpenMode.SAME_WINDOW, projectToClose)))
        }
        Assertions.assertEquals(listOf(projectToClose), ProjectManagerEx.getInstanceEx().openProjects.toList())
      }
    }

    Assertions.assertEquals(emptyList<Path>(), p3Support.childProcessProjectRoots)
    Assertions.assertEquals(ThreeState.UNSURE, TrustedProjects.getProjectTrustedState(projectRoot))
  }

  /**
   * Opens a project in this process when [canBeOpenedInThisProcess] accepts it. Records the other projects instead of starting a child process.
   */
  private class RecordingP3Support(private val canBeOpenedInThisProcess: (Path) -> Boolean) : P3Support {
    val childProcessProjectRoots: MutableList<Path> = CopyOnWriteArrayList()

    override fun isEnabled(): Boolean = true

    override fun canBeOpenedInThisProcess(projectStoreBaseDir: Path): Boolean = canBeOpenedInThisProcess.invoke(projectStoreBaseDir)

    override suspend fun openInChildProcess(projectStoreBaseDir: Path) {
      childProcessProjectRoots.add(projectStoreBaseDir)
    }
  }

  private suspend fun withP3Support(support: P3Support, action: suspend () -> Unit) {
    val oldSupport = P3SupportInstaller.installPerProcessInstanceSupportTemporarily(support)
    try {
      action()
    }
    finally {
      P3SupportInstaller.installPerProcessInstanceSupportTemporarily(oldSupport)
    }
  }

  private suspend fun withVetoedClose(projectToClose: Project, action: suspend () -> Unit) {
    val projectManager = ProjectManagerEx.getInstanceEx()
    val vetoListener = object : VetoableProjectManagerListener {
      override fun canClose(project: Project): Boolean = project !== projectToClose
    }
    projectManager.addProjectManagerListener(vetoListener)
    try {
      action()
    }
    finally {
      projectManager.removeProjectManagerListener(vetoListener)
    }
  }

  private suspend fun openProjectAndCheckTrustedState(projectRoot: Path, options: OpenProjectTask, expectedTrustedState: ThreeState) {
    ProjectManagerEx.getInstanceEx()
      .openProjectAsync(projectRoot, options)!!
      .awaitInitialisation()
      .useProjectAsync { project ->
        Assertions.assertEquals(expectedTrustedState, TrustedProjects.getProjectTrustedState(project))
      }
  }

  @ParameterizedTest
  @CsvSource(
    "TRUST_AND_OPEN, true, YES",
    "OPEN_IN_SAFE_MODE, true, NO",
    "CANCEL, false, UNSURE",
  )
  fun `trust dialog choice controls the attach to the current project`(
    openChoice: OpenUntrustedProjectChoice,
    expectedAttached: Boolean,
    expectedTrustedState: ThreeState,
  ): Unit = runBlocking {
    val projectRoot = testRoot.resolve("project")
    TrustedProjectStartupDialog.setDialogChoiceInTests(openChoice, asDisposable())
    val attachProcessor = RecordingAttachProcessor()
    ExtensionTestUtil.maskExtensions(ProjectAttachProcessor.EP_NAME, listOf(attachProcessor), asDisposable())

    withProjectToClose(GeneralSettings.OPEN_PROJECT_SAME_WINDOW_ATTACH) { projectToClose ->
      openProjectToAttach(projectRoot, projectToClose)
    }

    Assertions.assertEquals(if (expectedAttached) listOf(projectRoot) else emptyList<Path>(), attachProcessor.attachedDirs)
    Assertions.assertEquals(expectedTrustedState, TrustedProjects.getProjectTrustedState(projectRoot))
  }

  @Test
  fun `a task with a project to attach gives the project to the processor while the setting asks`(): Unit = runBlocking {
    val projectRoot = testRoot.resolve("project")
    TrustedProjectStartupDialog.setDialogChoiceInTests(OpenUntrustedProjectChoice.TRUST_AND_OPEN, asDisposable())
    val attachProcessor = RecordingAttachProcessor()
    ExtensionTestUtil.maskExtensions(ProjectAttachProcessor.EP_NAME, listOf(attachProcessor), asDisposable())

    // In a test, the question gives "new window". The project attaches only when the platform does not ask.
    withProjectToClose(GeneralSettings.OPEN_PROJECT_ASK) { projectToAttach ->
      openWithProjectToAttach(projectRoot, projectToAttach)
    }

    Assertions.assertEquals(listOf(projectRoot), attachProcessor.attachedDirs)
    Assertions.assertEquals(ThreeState.YES, TrustedProjects.getProjectTrustedState(projectRoot))
  }

  @Test
  fun `a project to attach stays open when the task forces the reuse of a frame`(): Unit = runBlocking {
    val projectRoot = testRoot.resolve("project")
    TrustedProjectStartupDialog.setDialogChoiceInTests(OpenUntrustedProjectChoice.TRUST_AND_OPEN, asDisposable())
    val attachProcessor = RecordingAttachProcessor()
    ExtensionTestUtil.maskExtensions(ProjectAttachProcessor.EP_NAME, listOf(attachProcessor), asDisposable())

    withProjectToClose(confirmOpenNewProject = null) { projectToAttach ->
      openWithProjectToAttach(projectRoot, projectToAttach, forceReuseFrame = true)
    }

    Assertions.assertEquals(listOf(projectRoot), attachProcessor.attachedDirs)
  }

  @Test
  fun `a task with no attach processor reuses the frame of the project to close and keeps the project to attach`(): Unit = runBlocking {
    val projectRoot = testRoot.resolve("project")
    TrustedProjects.setProjectTrusted(projectRoot, true)
    ExtensionTestUtil.maskExtensions(ProjectAttachProcessor.EP_NAME, emptyList(), asDisposable())
    val projectManager = ProjectManagerEx.getInstanceEx()

    withProjectToClose(GeneralSettings.OPEN_PROJECT_SAME_WINDOW) { projectToClose ->
      val projectToAttachRoot = testRoot.resolve("projectToAttach")
      TrustedProjects.setProjectTrusted(projectToAttachRoot, true)
      val projectToAttach = projectManager.openProjectAsync(projectToAttachRoot, OpenProjectTask {
        projectName = "projectToAttach"
        forceOpenInNewFrame = true
      }) ?: Assertions.fail("The project to attach did not open")
      var project: Project? = null
      try {
        project = projectManager.openProjectAsync(projectRoot, OpenProjectTask {
          projectName = "project"
          this.projectToAttach = projectToAttach
          this.projectToClose = projectToClose
        })

        Assertions.assertNotNull(project, "With no attach processor, the project opens in a frame")
        Assertions.assertTrue(projectManager.isProjectOpened(projectToAttach), "The project to attach stays open")
        Assertions.assertFalse(projectManager.isProjectOpened(projectToClose), "The project to close gives its frame")
      }
      finally {
        for (opened in listOfNotNull(project, projectToAttach)) {
          if (projectManager.isProjectOpened(opened)) {
            opened.closeProjectAsync()
          }
        }
      }
    }
  }

  @ParameterizedTest
  @ValueSource(booleans = [true, false])
  fun `failed attach does not open the project`(hasAttachProcessor: Boolean): Unit = runBlocking {
    val projectRoot = testRoot.resolve("project")
    TrustedProjectStartupDialog.setDialogChoiceInTests(OpenUntrustedProjectChoice.TRUST_AND_OPEN, asDisposable())
    val attachProcessor = RecordingAttachProcessor(attachResult = false)
    val attachProcessors = if (hasAttachProcessor) listOf(attachProcessor) else emptyList()
    ExtensionTestUtil.maskExtensions(ProjectAttachProcessor.EP_NAME, attachProcessors, asDisposable())

    withProjectToClose(GeneralSettings.OPEN_PROJECT_SAME_WINDOW_ATTACH) { projectToClose ->
      openProjectToAttach(projectRoot, projectToClose)
      Assertions.assertEquals(listOf(projectToClose), ProjectManagerEx.getInstanceEx().openProjects.toList())
    }

    Assertions.assertEquals(if (hasAttachProcessor) listOf(projectRoot) else emptyList<Path>(), attachProcessor.attachedDirs)
    Assertions.assertEquals(ThreeState.YES, TrustedProjects.getProjectTrustedState(projectRoot))
  }

  private suspend fun openProjectToAttach(projectRoot: Path, projectToClose: Project) {
    val project = ProjectManagerEx.getInstanceEx().openProjectAsync(projectRoot, OpenProjectTask {
      this.projectName = "project"
      this.projectToClose = projectToClose
    })
    Assertions.assertNull(project)
    Assertions.assertTrue(ProjectManagerEx.getInstanceEx().isProjectOpened(projectToClose))
  }

  private suspend fun openWithProjectToAttach(projectRoot: Path, projectToAttach: Project, forceReuseFrame: Boolean = false) {
    val project = ProjectManagerEx.getInstanceEx().openProjectAsync(projectRoot, OpenProjectTask {
      this.projectName = "project"
      this.projectToAttach = projectToAttach
      this.forceReuseFrame = forceReuseFrame
    })
    project?.closeProjectAsync()
    Assertions.assertNull(project, "The project attaches and opens no frame")
    Assertions.assertTrue(ProjectManagerEx.getInstanceEx().isProjectOpened(projectToAttach), "The project to attach stays open")
  }

  private class RecordingAttachProcessor(private val attachResult: Boolean = true) : ProjectAttachProcessor() {
    val attachedDirs: MutableList<Path> = CopyOnWriteArrayList()
    override suspend fun attachToProjectAsync(
      project: Project,
      projectDir: Path,
      callback: ProjectOpenedCallback?,
      beforeOpen: (suspend (Project) -> Boolean)?,
    ): Boolean {
      attachedDirs.add(projectDir)
      return attachResult
    }
  }

  private suspend fun withProjectToClose(mode: OpenMode, action: suspend (projectToClose: Project?) -> Unit) {
    when (mode) {
      OpenMode.NO_OPEN_PROJECT -> action(null)
      OpenMode.SAME_WINDOW -> withProjectToClose(GeneralSettings.OPEN_PROJECT_SAME_WINDOW, action)
      OpenMode.NEW_WINDOW -> withProjectToClose(GeneralSettings.OPEN_PROJECT_NEW_WINDOW, action)
      OpenMode.FORCE_NEW_FRAME, OpenMode.FORCE_REUSE_FRAME -> withProjectToClose(confirmOpenNewProject = null, action)
    }
  }

  private suspend fun withProjectToClose(confirmOpenNewProject: Int?, action: suspend (projectToClose: Project) -> Unit) {
    val generalSettings = GeneralSettings.getInstance()
    val oldConfirmOpenNewProject = generalSettings.confirmOpenNewProject
    var projectToClose: Project? = null
    try {
      if (confirmOpenNewProject != null) {
        generalSettings.confirmOpenNewProject = confirmOpenNewProject
      }

      val projectToCloseRoot = testRoot.resolve("projectToClose")
      TrustedProjects.setProjectTrusted(projectToCloseRoot, true)
      val openedProject = ProjectManagerEx.getInstanceEx().openProjectAsync(projectToCloseRoot, OpenProjectTask {
        projectName = "projectToClose"
        forceOpenInNewFrame = true
      }) ?: Assertions.fail("The current project did not open")
      projectToClose = openedProject
      action(openedProject)
    }
    finally {
      generalSettings.confirmOpenNewProject = oldConfirmOpenNewProject
      if (projectToClose != null && ProjectManagerEx.getInstanceEx().isProjectOpened(projectToClose)) {
        projectToClose.closeProjectAsync()
      }
    }
  }

  private fun createOpenProjectTask(mode: OpenMode, projectToClose: Project?): OpenProjectTask = OpenProjectTask {
    this.projectName = "project"
    this.projectToClose = projectToClose
    this.forceOpenInNewFrame = mode == OpenMode.FORCE_NEW_FRAME
    this.forceReuseFrame = mode == OpenMode.FORCE_REUSE_FRAME
  }

  private suspend fun Project.awaitInitialisation() = withProjectAsync { project ->
    IndexingTestUtil.suspendUntilIndexesAreReady(project)
  }
}