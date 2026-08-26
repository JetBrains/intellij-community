// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.trustedProjects

import com.intellij.ide.CommandLineProcessor
import com.intellij.ide.impl.TrustedPaths
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.UiWithModelAccess
import com.intellij.openapi.application.writeIntentReadAction
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.SystemProperty
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import com.intellij.util.ThreeState
import com.intellij.util.application
import com.intellij.util.asDisposable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

@TestApplication
@SystemProperty("idea.trust.headless.disabled", "false")
class TrustedFilesTest {
  private val projectFixture = projectFixture(openAfterCreation = true)
  private val tempPath by tempPathFixture()

  @AfterEach
  fun tearDown() {
    // the mark store is application-level and would leak into the next test
    ExternallyOpenedFiles.getInstance().loadState(ExternallyOpenedFiles.State())
  }

  @Test
  fun `every file is trusted while the safe mode is off`(): Unit = timeoutRunBlocking {
    val project = projectFixture.get()
    val outsideFile = tempPath.resolve("outside.txt")
    Files.writeString(outsideFile, "text")
    val file = requireNotNull(LocalFileSystem.getInstance().refreshAndFindFileByNioFile(outsideFile))
    TrustedFiles.markExternallyOpened(file)

    // the registry key is off by default
    assertTrue(TrustedFiles.isTrusted(file, project))
  }

  @Test
  fun `an unmarked outside file is trusted`(): Unit = timeoutRunBlocking {
    Registry.get(TrustedFiles.SAFE_MODE_REGISTRY_KEY).setValue(true, asDisposable())
    val project = projectFixture.get()

    // an IDE-internal file (a scratch, a console, the custom VM options file) is opened
    // without the external-source mark and must keep the full functionality
    val internalFile = tempPath.resolve("internal.txt")
    Files.writeString(internalFile, "text")
    val file = requireNotNull(LocalFileSystem.getInstance().refreshAndFindFileByNioFile(internalFile))

    assertTrue(TrustedFiles.isTrusted(file, project))
  }

  @Test
  fun `marking invalidates the cached verdict`(): Unit = timeoutRunBlocking {
    Registry.get(TrustedFiles.SAFE_MODE_REGISTRY_KEY).setValue(true, asDisposable())
    val project = projectFixture.get()

    val outsideFile = tempPath.resolve("outside.txt")
    Files.writeString(outsideFile, "text")
    val file = requireNotNull(LocalFileSystem.getInstance().refreshAndFindFileByNioFile(outsideFile))

    assertTrue(TrustedFiles.isTrusted(file, project))
    TrustedFiles.markExternallyOpened(file)
    assertFalse(TrustedFiles.isTrusted(file, project))
  }

  @Test
  fun `marking an open file downgrades the verdict and keeps the editor open`(): Unit =
    timeoutRunBlocking(context = Dispatchers.UiWithModelAccess) {
      Registry.get(TrustedFiles.SAFE_MODE_REGISTRY_KEY).setValue(true, asDisposable())
      val project = projectFixture.get()

      val outsidePath = tempPath.resolve("open-then-mark.txt")
      Files.writeString(outsidePath, "text")
      val file = requireNotNull(LocalFileSystem.getInstance().refreshAndFindFileByNioFile(outsidePath))

      // the file opens with the full functionality first, e.g. from a console hyperlink
      val fileEditorManager = FileEditorManager.getInstance(project)
      writeIntentReadAction { fileEditorManager.openFile(file, true) }
      assertTrue(TrustedFiles.isTrusted(file, project))

      TrustedFiles.markExternallyOpened(file)
      assertFalse(TrustedFiles.isTrusted(file, project))

      // the downgrade schedules an editor refresh; let it run before the project closes
      withContext(Dispatchers.EDT) { }
      assertTrue(fileEditorManager.isFileOpen(file))
      writeIntentReadAction { fileEditorManager.closeFile(file) }
    }

  @Test
  fun `an evicted mark lifts the safe mode`(): Unit = timeoutRunBlocking {
    Registry.get(TrustedFiles.SAFE_MODE_REGISTRY_KEY).setValue(true, asDisposable())
    val project = projectFixture.get()
    ExternallyOpenedFiles.getInstance().loadState(ExternallyOpenedFiles.State())

    val outsidePath = tempPath.resolve("evicted.txt")
    Files.writeString(outsidePath, "text")
    val file = requireNotNull(LocalFileSystem.getInstance().refreshAndFindFileByNioFile(outsidePath))
    TrustedFiles.markExternallyOpened(file)
    assertFalse(TrustedFiles.isTrusted(file, project))

    // 100 more externally opened files push the first mark out of the capped list
    for (i in 0 until 100) {
      val fillerPath = tempPath.resolve("filler-$i.txt")
      Files.writeString(fillerPath, "text")
      val filler = requireNotNull(LocalFileSystem.getInstance().refreshAndFindFileByNioFile(fillerPath))
      TrustedFiles.markExternallyOpened(filler)
    }

    assertFalse(ExternallyOpenedFiles.getInstance().isMarked(outsidePath))
    assertTrue(TrustedFiles.isTrusted(file, project))
  }

  @Test
  fun `marked outside files are untrusted until their location is trusted`(): Unit =
    timeoutRunBlocking(context = Dispatchers.UiWithModelAccess) {
      Registry.get(TrustedFiles.SAFE_MODE_REGISTRY_KEY).setValue(true, asDisposable())
      val project = projectFixture.get()

      val dir = tempPath.resolve("outside")
      Files.createDirectories(dir)
      val outsideFile = dir.resolve("data.txt")
      val siblingFile = dir.resolve("sibling.txt")
      Files.writeString(outsideFile, "text")
      Files.writeString(siblingFile, "text")
      val file = requireNotNull(LocalFileSystem.getInstance().refreshAndFindFileByNioFile(outsideFile))
      val sibling = requireNotNull(LocalFileSystem.getInstance().refreshAndFindFileByNioFile(siblingFile))
      TrustedFiles.markExternallyOpened(file)
      TrustedFiles.markExternallyOpened(sibling)

      // a file inside the project's own roots is trusted even when it is marked
      val insidePath = Path.of(project.basePath!!).resolve("inside.txt")
      Files.writeString(insidePath, "text")
      // refreshing under an open project's root fires VFS events synchronously and needs the write-intent lock
      val inside = requireNotNull(writeIntentReadAction {
        LocalFileSystem.getInstance().refreshAndFindFileByNioFile(insidePath)
      })
      TrustedFiles.markExternallyOpened(inside)
      assertTrue(TrustedFiles.isTrusted(inside, project))

      assertFalse(TrustedFiles.isTrusted(file, project))
      assertFalse(TrustedFiles.isTrusted(sibling, project))

      // trusting the exact file path covers only that file; the cached verdict is invalidated by the trust event
      TrustedProjects.setProjectTrusted(outsideFile, true)
      assertTrue(TrustedFiles.isTrusted(file, project))
      assertFalse(TrustedFiles.isTrusted(sibling, project))

      // a trusted ancestor directory covers the sibling too
      TrustedProjects.setProjectTrusted(dir, true)
      assertTrue(TrustedFiles.isTrusted(sibling, project))
    }

  @Test
  fun `a revoked trusted location returns a marked file to the safe mode`(): Unit = timeoutRunBlocking {
    Registry.get(TrustedFiles.SAFE_MODE_REGISTRY_KEY).setValue(true, asDisposable())
    val project = projectFixture.get()

    val outsideFile = tempPath.resolve("revoked.txt")
    Files.writeString(outsideFile, "text")
    val file = requireNotNull(LocalFileSystem.getInstance().refreshAndFindFileByNioFile(outsideFile))
    TrustedFiles.markExternallyOpened(file)

    TrustedProjects.setProjectTrusted(outsideFile, true)
    assertTrue(TrustedFiles.isTrusted(file, project))

    // the mark outlives the trust grant, so a revoked location is untrusted again
    val trustedPaths = TrustedPaths.getInstance()
    trustedPaths.setExplicitlyTrustedPaths(trustedPaths.getExplicitlyTrustedPaths() - outsideFile.toString())
    // the settings page fires the trust event itself after it applies the list change
    val locatedFile = TrustedProjectsLocator.locateProject(outsideFile, project = null)
    application.messageBus.syncPublisher(TrustedProjectsListener.TOPIC).onProjectUntrusted(locatedFile)
    assertFalse(TrustedFiles.isTrusted(file, project))
  }

  @Test
  fun `a file opened from the command line is marked`(): Unit = timeoutRunBlocking {
    Registry.get(TrustedFiles.SAFE_MODE_REGISTRY_KEY).setValue(true, asDisposable())
    val project = projectFixture.get()

    val cliFile = tempPath.resolve("cli.txt")
    Files.writeString(cliFile, "text")

    CommandLineProcessor.processExternalCommandLine(listOf(cliFile.toString()), null)

    val file = requireNotNull(LocalFileSystem.getInstance().refreshAndFindFileByNioFile(cliFile))
    assertFalse(TrustedFiles.isTrusted(file, project))
    // let the navigation scheduled by the command line processor finish, then release the editor
    withContext(Dispatchers.EDT) {
      FileEditorManager.getInstance(project).closeFile(file)
    }
  }

  @Test
  fun `the mark list is capped`(): Unit = timeoutRunBlocking {
    val store = ExternallyOpenedFiles.getInstance()
    store.loadState(ExternallyOpenedFiles.State())

    val first = tempPath.resolve("file-0.txt")
    for (i in 0 until 101) {
      store.mark(tempPath.resolve("file-$i.txt"))
    }
    assertFalse(store.isMarked(first))
    assertTrue(store.isMarked(tempPath.resolve("file-100.txt")))

    // a repeated mark moves the entry to the fresh end instead of duplicating it
    store.loadState(ExternallyOpenedFiles.State())
    store.mark(first)
    for (i in 1 until 100) {
      store.mark(tempPath.resolve("file-$i.txt"))
    }
    store.mark(first)
    store.mark(tempPath.resolve("file-100.txt"))
    assertTrue(store.isMarked(first))
  }

  @Test
  fun `explicitly trusted paths are listed in the settings and can be revoked`(): Unit = timeoutRunBlocking {
    val trustedFile = tempPath.resolve("listed.txt")
    Files.writeString(trustedFile, "text")

    TrustedProjects.setProjectTrusted(trustedFile, true)
    val trustedPaths = TrustedPaths.getInstance()
    assertTrue(trustedPaths.getExplicitlyTrustedPaths().contains(trustedFile.toString()))

    // removing an entry forgets the decision instead of marking the path untrusted
    trustedPaths.setExplicitlyTrustedPaths(trustedPaths.getExplicitlyTrustedPaths() - trustedFile.toString())
    assertFalse(trustedPaths.getExplicitlyTrustedPaths().contains(trustedFile.toString()))
    assertEquals(ThreeState.UNSURE, TrustedProjects.getProjectTrustedState(trustedFile))
  }
}
