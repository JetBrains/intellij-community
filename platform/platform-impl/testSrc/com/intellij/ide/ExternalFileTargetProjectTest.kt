// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide

import com.intellij.ide.impl.ProjectUtil
import com.intellij.ide.trustedProjects.ExternallyOpenedFiles
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ex.ProjectManagerEx
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.platform.ExternalFileTargetProjectProvider
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.common.waitUntil
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import com.intellij.util.application
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.nio.file.Files
import java.nio.file.Path

/**
 * An [ExternalFileTargetProjectProvider] names the project of a file that comes from outside the IDE, and each mark of such a file
 * reaches [ExternallyOpenedFileListener].
 */
@TestApplication
@Timeout(60)
internal class ExternalFileTargetProjectTest {
  private val firstProjectFixture = projectFixture(openAfterCreation = true)
  private val secondProjectFixture = projectFixture(openAfterCreation = true)
  private val tempPath by tempPathFixture()

  @TestDisposable
  lateinit var disposable: Disposable

  @AfterEach
  fun tearDown() {
    // the mark store is application-level and would leak into the next test
    ExternallyOpenedFiles.getInstance().loadState(ExternallyOpenedFiles.State())
  }

  @Test
  fun `the command line opens a file in the project that a provider names`(): Unit = timeoutRunBlocking {
    val target = targetProject()
    maskProviders(target)
    val path = createFile("cli.txt")

    assertSame(target, CommandLineProcessor.processExternalCommandLine(listOf(path.toString()), null).project)

    val file = virtualFile(path)
    waitUntil("The file opens in the named project") { FileEditorManager.getInstance(target).isFileOpen(file) }
    closeFile(target, file)
  }

  @Test
  fun `a file association opens a file in the project that a provider names`(): Unit = timeoutRunBlocking {
    val target = targetProject()
    maskProviders(target)
    val path = createFile("association.txt")

    assertSame(target, ProjectUtil.openOrImportFilesAsync(listOf(path), "test"))

    val file = virtualFile(path)
    waitUntil("The file opens in the named project") { FileEditorManager.getInstance(target).isFileOpen(file) }
    closeFile(target, file)
  }

  @Test
  fun `each mark of a file reaches the listener`(): Unit = timeoutRunBlocking {
    val marked = ArrayList<VirtualFile>()
    application.messageBus.connect(disposable).subscribe(ExternallyOpenedFileListener.TOPIC, ExternallyOpenedFileListener(marked::add))
    val file = virtualFile(createFile("marked.txt"))

    TrustedFiles.markExternallyOpened(file)
    TrustedFiles.markExternallyOpened(file)

    assertEquals(listOf(file, file), marked)
  }

  /** The project that the platform does not select on its own: without a focused frame, it takes the first open project. */
  private fun targetProject(): Project {
    firstProjectFixture.get()
    secondProjectFixture.get()
    return ProjectManagerEx.getOpenProjects().last()
  }

  /** A test masks the providers one time: a second mask of the same point is read-only. */
  private fun maskProviders(target: Project) {
    val provider = object : ExternalFileTargetProjectProvider {
      override suspend fun getTargetProject(file: Path): Project = target
    }
    ExtensionTestUtil.maskExtensions(ExternalFileTargetProjectProvider.EP_NAME, listOf(provider), disposable)
  }

  private fun createFile(name: String): Path = Files.writeString(tempPath.resolve(name), "text")

  private fun virtualFile(path: Path): VirtualFile = requireNotNull(VirtualFileManager.getInstance().refreshAndFindFileByNioPath(path))

  private suspend fun closeFile(project: Project, file: VirtualFile) {
    withContext(Dispatchers.EDT) {
      FileEditorManager.getInstance(project).closeFile(file)
    }
  }
}
