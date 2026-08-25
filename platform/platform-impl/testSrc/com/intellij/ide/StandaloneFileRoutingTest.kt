// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide

import com.intellij.ide.impl.OpenProjectTask
import com.intellij.ide.impl.ProjectUtil
import com.intellij.ide.lightEdit.LightEditService
import com.intellij.ide.lightEdit.LightEditUtil
import com.intellij.ide.lightEdit.LightEditorInfo
import com.intellij.ide.lightEdit.LightEditorListener
import com.intellij.ide.lightEdit.LightEditorManager
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Document
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.ex.WelcomeScreenProjectProvider
import com.intellij.platform.CommandLineProjectOpenProcessor
import com.intellij.platform.PlatformProjectOpenProcessor
import com.intellij.projectImport.ProjectOpenProcessor
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.replaceService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

@TestApplication
@Timeout(30)
internal class StandaloneFileRoutingTest {
  @TestDisposable
  lateinit var disposable: Disposable

  private val project: Project
    get() = ProjectManager.getInstance().defaultProject

  private lateinit var welcomeScreenProvider: TestWelcomeScreenProjectProvider
  private lateinit var welcomeScreenProcessor: TestWelcomeScreenCommandLineProcessor
  private lateinit var lightEditService: RecordingLightEditService

  @BeforeEach
  fun setUp() {
    welcomeScreenProvider = TestWelcomeScreenProjectProvider()
    welcomeScreenProcessor = TestWelcomeScreenCommandLineProcessor(project)
    lightEditService = RecordingLightEditService(project)

    ExtensionTestUtil.maskExtensions(
      ExtensionPointName<WelcomeScreenProjectProvider>("com.intellij.welcomeScreenProjectProvider"),
      listOf(welcomeScreenProvider),
      disposable,
    )
    ExtensionTestUtil.maskExtensions(
      ProjectOpenProcessor.EXTENSION_POINT_NAME,
      listOf(welcomeScreenProcessor, PlatformProjectOpenProcessor()),
      disposable,
    )
    ApplicationManager.getApplication().replaceService(LightEditService::class.java, lightEditService, disposable)
  }

  @Test
  fun `file association avoids LightEdit when welcome project claims file`(@TempDir tempDir: Path): Unit = timeoutRunBlocking {
    welcomeScreenProvider.claimFiles = true
    val file = createFile(tempDir)

    assertSame(project, ProjectUtil.openOrImportFilesAsync(listOf(file), "test"))
    assertEquals(1, welcomeScreenProcessor.openCount)
    assertEquals(0, lightEditService.openCount)
  }

  @Test
  fun `file association retains LightEdit when welcome project does not claim file`(@TempDir tempDir: Path): Unit = timeoutRunBlocking {
    welcomeScreenProvider.claimFiles = false
    val file = createFile(tempDir)

    assertSame(project, ProjectUtil.openOrImportFilesAsync(listOf(file), "test"))
    assertEquals(0, welcomeScreenProcessor.openCount)
    assertEquals(1, lightEditService.openCount)
  }

  @Test
  fun `edit option avoids LightEdit when welcome project claims file`(@TempDir tempDir: Path): Unit = timeoutRunBlocking {
    welcomeScreenProvider.claimFiles = true
    val file = createFile(tempDir)

    assertSame(project, CommandLineProcessor.processExternalCommandLine(listOf("--edit", file.toString()), null).project)
    assertEquals(0, lightEditService.openCount)
  }

  @Test
  fun `edit option retains LightEdit when welcome project does not claim file`(@TempDir tempDir: Path): Unit = timeoutRunBlocking {
    welcomeScreenProvider.claimFiles = false
    val file = createFile(tempDir)

    assertSame(project, CommandLineProcessor.processExternalCommandLine(listOf("--edit", file.toString()), null).project)
    assertEquals(1, lightEditService.openCount)
  }

  private fun createFile(tempDir: Path): Path = Files.writeString(tempDir.resolve("standalone.txt"), "text")

  private class TestWelcomeScreenProjectProvider : WelcomeScreenProjectProvider() {
    var claimFiles: Boolean = false

    override fun canOpenFilesFromSystemFileManager(filePath: Path): Boolean = claimFiles && Files.isRegularFile(filePath)

    override fun getWelcomeScreenProjectName(): String = "TestWelcomeProject"

    override fun doIsWelcomeScreenProject(project: Project): Boolean = false

    override fun doIsForceDisabledFileColors(): Boolean = true

    override fun doGetCreateNewFileProjectPrefix(): String = "testProject"
  }

  private class TestWelcomeScreenCommandLineProcessor(
    private val project: Project,
  ) : ProjectOpenProcessor(), CommandLineProjectOpenProcessor {
    var openCount: Int = 0

    override val name: String = "test welcome screen"

    override fun canOpenProject(file: VirtualFile): Boolean = false

    override suspend fun openProjectAsync(virtualFile: VirtualFile, projectOpenOptions: ProjectOpenOptions): Project? = null

    override suspend fun openProjectAndFile(file: Path, tempProject: Boolean, options: OpenProjectTask): Project? {
      if (tempProject || !WelcomeScreenProjectProvider.canOpenFilesFromSystemFileManager(file)) {
        return null
      }
      openCount++
      return project
    }
  }

  private class RecordingLightEditService(override val project: Project) : LightEditService {
    var openCount: Int = 0

    override fun createNewDocument(preferredSavePath: Path?): LightEditorInfo? = null
    override fun saveToAnotherFile(file: VirtualFile) = Unit
    override fun showEditorWindow() = Unit

    override fun openFile(file: VirtualFile): Project {
      openCount++
      return project
    }

    override fun openFile(path: Path, suggestSwitchToProject: Boolean): Project {
      openCount++
      return project
    }

    override var isAutosaveMode: Boolean = false
    override fun closeEditorWindow(): Boolean = false
    override val editorManager: LightEditorManager = EmptyLightEditorManager
    override fun getSelectedFile(): VirtualFile? = null
    override fun getSelectedFileEditor(): FileEditor? = null
    override fun updateFileStatus(files: Collection<VirtualFile>) = Unit
    override fun saveNewDocuments() = Unit
    override fun isTabNavigationAvailable(navigationAction: AnAction): Boolean = false
    override fun navigateToTab(navigationAction: AnAction) = Unit
    override val isPreferProjectMode: Boolean = false
    override fun isLightEditEnabled(): Boolean = true
    override fun isLightEditProject(project: Project): Boolean = project === this.project
    override fun isForceOpenInLightEditMode(): Boolean = LightEditUtil.isForceOpenInLightEditMode()
  }

  private object EmptyLightEditorManager : LightEditorManager {
    override fun addListener(listener: LightEditorListener) = Unit
    override fun addListener(listener: LightEditorListener, disposable: Disposable) = Unit
    override fun saveAs(info: LightEditorInfo, targetFile: VirtualFile): LightEditorInfo = error("Not expected")
    override fun createEmptyEditor(preferredName: String?): LightEditorInfo = error("Not expected")
    override fun createEditor(file: VirtualFile): LightEditorInfo? = null
    override fun closeEditor(editorInfo: LightEditorInfo) = Unit
    override fun containsUnsavedDocuments(): Boolean = false
    override fun isImplicitSaveAllowed(document: Document): Boolean = true
    override fun getOpenFiles(): Collection<VirtualFile> = emptyList()
    override fun getEditors(virtualFile: VirtualFile): Collection<LightEditorInfo> = emptyList()
    override fun isFileOpen(file: VirtualFile): Boolean = false
  }
}
