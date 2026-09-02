// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.browsers

import com.intellij.ide.trustedProjects.ExternallyOpenedFiles
import com.intellij.ide.trustedProjects.TrustedFiles
import com.intellij.ide.trustedProjects.TrustedProjects
import com.intellij.openapi.application.UiWithModelAccess
import com.intellij.openapi.application.writeIntentReadAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.TestDialog
import com.intellij.openapi.ui.TestDialogManager
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.SystemProperty
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import com.intellij.util.ThreeState
import com.intellij.util.Urls
import com.intellij.util.asDisposable
import kotlinx.coroutines.Dispatchers
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

/**
 * Tests the external-browser trust gate for a standalone file in the safe mode
 * (see [BrowserLauncherImpl.findStandaloneFile] and `BrowserLauncherImpl.canBrowse`).
 */
@TestApplication
@SystemProperty("idea.trust.headless.disabled", "false")
class BrowserLauncherSafeModeTest {
  private val projectFixture = projectFixture(openAfterCreation = true)
  private val tempPath by tempPathFixture()

  private object Launcher : BrowserLauncherImpl() {
    fun canBrowseForTest(project: Project?, uri: String): Boolean = canBrowse(project, uri)
  }

  @AfterEach
  fun tearDown() {
    // the mark store is application-level and would leak into the next test
    ExternallyOpenedFiles.getInstance().loadState(ExternallyOpenedFiles.State())
  }

  private fun createOutsideFile(name: String): VirtualFile {
    val path = tempPath.resolve(name)
    Files.writeString(path, "<html></html>")
    return requireNotNull(LocalFileSystem.getInstance().refreshAndFindFileByNioFile(path))
  }

  private fun fileUrl(file: VirtualFile): String = Urls.newFromVirtualFile(file).toExternalForm()

  @Test
  fun `the helper finds a marked outside file by its encoded file url`(): Unit = timeoutRunBlocking {
    Registry.get(TrustedFiles.SAFE_MODE_REGISTRY_KEY).setValue(true, asDisposable())
    val project = projectFixture.get()

    val file = createOutsideFile("a b.html")
    TrustedFiles.markExternallyOpened(file)

    val url = fileUrl(file)
    assertTrue(url.contains("%20"), "expected an encoded url, got: $url")
    assertEquals(file, BrowserLauncherImpl.findStandaloneFile(project, url))
  }

  @Test
  fun `the helper skips other urls and files`(): Unit = timeoutRunBlocking(context = Dispatchers.UiWithModelAccess) {
    Registry.get(TrustedFiles.SAFE_MODE_REGISTRY_KEY).setValue(true, asDisposable())
    val project = projectFixture.get()

    assertNull(BrowserLauncherImpl.findStandaloneFile(project, "https://jetbrains.com"))

    // an unmarked outside file keeps the project-level trust model
    val unmarked = createOutsideFile("unmarked.html")
    assertNull(BrowserLauncherImpl.findStandaloneFile(project, fileUrl(unmarked)))

    // a file inside the project's own roots keeps the project-level trust model even when it is marked
    val insidePath = Path.of(project.basePath!!).resolve("inside.html")
    Files.writeString(insidePath, "<html></html>")
    // refreshing under an open project's root fires VFS events synchronously and needs the write-intent lock
    val inside = requireNotNull(writeIntentReadAction {
      LocalFileSystem.getInstance().refreshAndFindFileByNioFile(insidePath)
    })
    TrustedFiles.markExternallyOpened(inside)
    assertNull(BrowserLauncherImpl.findStandaloneFile(project, fileUrl(inside)))
  }

  @Test
  fun `the helper is inactive while the safe mode is off`(): Unit = timeoutRunBlocking {
    // the registry key is off by default
    val project = projectFixture.get()

    val file = createOutsideFile("off.html")
    TrustedFiles.markExternallyOpened(file)

    assertNull(BrowserLauncherImpl.findStandaloneFile(project, fileUrl(file)))
  }

  @Test
  fun `an untrusted standalone file in a trusted project needs a confirmation`(): Unit = timeoutRunBlocking {
    Registry.get(TrustedFiles.SAFE_MODE_REGISTRY_KEY).setValue(true, asDisposable())
    val project = projectFixture.get()
    TrustedProjects.setProjectTrusted(project, true)

    val file = createOutsideFile("index.html")
    TrustedFiles.markExternallyOpened(file)
    val filePath = requireNotNull(file.fileSystem.getNioPath(file))

    // "Open": browse once, no trust grant
    TestDialogManager.setTestDialog(TestDialog { 0 }, asDisposable())
    assertTrue(Launcher.canBrowseForTest(project, fileUrl(file)))
    assertEquals(ThreeState.UNSURE, TrustedProjects.getProjectTrustedState(filePath))

    // Cancel: no browse
    TestDialogManager.setTestDialog(TestDialog { 2 }, asDisposable())
    assertFalse(Launcher.canBrowseForTest(project, fileUrl(file)))
    assertEquals(ThreeState.UNSURE, TrustedProjects.getProjectTrustedState(filePath))

    // "Trust File and Open": browse, and the file location becomes trusted
    TestDialogManager.setTestDialog(TestDialog { 1 }, asDisposable())
    assertTrue(Launcher.canBrowseForTest(project, fileUrl(file)))
    assertEquals(ThreeState.YES, TrustedProjects.getProjectTrustedState(filePath))
    assertTrue(TrustedFiles.isTrusted(file, project))

    // the trusted file opens without a dialog from now on
    TestDialogManager.setTestDialog(TestDialog { throw AssertionError("no dialog expected") }, asDisposable())
    assertTrue(Launcher.canBrowseForTest(project, fileUrl(file)))
  }

  @Test
  fun `an unmarked file in a trusted project opens without a dialog`(): Unit = timeoutRunBlocking {
    Registry.get(TrustedFiles.SAFE_MODE_REGISTRY_KEY).setValue(true, asDisposable())
    val project = projectFixture.get()
    TrustedProjects.setProjectTrusted(project, true)

    val file = createOutsideFile("plain.html")

    TestDialogManager.setTestDialog(TestDialog { throw AssertionError("no dialog expected") }, asDisposable())
    assertTrue(Launcher.canBrowseForTest(project, fileUrl(file)))
  }

  @Test
  fun `a standalone file in an untrusted project gets the file dialog`(): Unit = timeoutRunBlocking {
    Registry.get(TrustedFiles.SAFE_MODE_REGISTRY_KEY).setValue(true, asDisposable())
    val project = projectFixture.get()
    TrustedProjects.setProjectTrusted(project, false)

    val file = createOutsideFile("outside.html")
    TrustedFiles.markExternallyOpened(file)
    val filePath = requireNotNull(file.fileSystem.getNioPath(file))

    // the trust button grants trust to the file location, not to the project
    TestDialogManager.setTestDialog(TestDialog { 1 }, asDisposable())
    assertTrue(Launcher.canBrowseForTest(project, fileUrl(file)))
    assertEquals(ThreeState.YES, TrustedProjects.getProjectTrustedState(filePath))
    assertFalse(TrustedProjects.isProjectTrusted(project))
  }

  @Test
  fun `an untrusted project still confirms a link`(): Unit = timeoutRunBlocking {
    Registry.get(TrustedFiles.SAFE_MODE_REGISTRY_KEY).setValue(true, asDisposable())
    val project = projectFixture.get()
    TrustedProjects.setProjectTrusted(project, false)

    TestDialogManager.setTestDialog(TestDialog { 0 }, asDisposable())
    assertTrue(Launcher.canBrowseForTest(project, "https://jetbrains.com"))

    TestDialogManager.setTestDialog(TestDialog { 2 }, asDisposable())
    assertFalse(Launcher.canBrowseForTest(project, "https://jetbrains.com"))
  }
}
