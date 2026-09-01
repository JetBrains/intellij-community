// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.trustedProjects.impl

import com.intellij.ide.impl.TrustedPaths
import com.intellij.ide.trustedProjects.ExternallyOpenedFiles
import com.intellij.ide.trustedProjects.TrustedFiles
import com.intellij.ide.trustedProjects.TrustedProjects
import com.intellij.openapi.application.UiWithModelAccess
import com.intellij.openapi.application.writeIntentReadAction
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.SystemProperty
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import com.intellij.util.asDisposable
import kotlinx.coroutines.Dispatchers
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

@TestApplication
@SystemProperty("idea.trust.headless.disabled", "false")
class UntrustedFileNotificationProviderTest {
  private val hostFixture = projectFixture(openAfterCreation = true)
  private val tempPath by tempPathFixture()

  @AfterEach
  fun tearDown() {
    // the mark store is application-level and would leak into the next test
    ExternallyOpenedFiles.getInstance().loadState(ExternallyOpenedFiles.State())
  }

  @Test
  fun `banner is shown for untrusted files until the file location is trusted`(): Unit =
    timeoutRunBlocking(context = Dispatchers.UiWithModelAccess) {
      Registry.get(TrustedFiles.SAFE_MODE_REGISTRY_KEY).setValue(true, asDisposable())
      val host = hostFixture.get()

      // precondition: the host project itself is trusted, otherwise the project-level banner owns the editor
      if (!TrustedProjects.isProjectTrusted(host)) {
        TrustedProjects.setProjectTrusted(host, true)
      }
      assertTrue(TrustedProjects.isProjectTrusted(host))

      val outsideFile = tempPath.resolve("outside").resolve("data.txt")
      Files.createDirectories(outsideFile.parent)
      Files.writeString(outsideFile, "text")
      val file = requireNotNull(LocalFileSystem.getInstance().refreshAndFindFileByNioFile(outsideFile))
      TrustedFiles.markExternallyOpened(file)

      // an IDE-internal file is not marked as externally opened and needs no banner
      val internalPath = tempPath.resolve("outside").resolve("internal.txt")
      Files.writeString(internalPath, "text")
      val internalFile = requireNotNull(LocalFileSystem.getInstance().refreshAndFindFileByNioFile(internalPath))

      // a file inside the host project's roots needs no banner even when it is marked
      val hostOwnedPath = Path.of(host.basePath!!).resolve("host-owned.txt")
      Files.writeString(hostOwnedPath, "text")
      // refreshing under an open project's root fires VFS events synchronously and needs the write-intent lock
      val hostOwnedFile = requireNotNull(writeIntentReadAction {
        LocalFileSystem.getInstance().refreshAndFindFileByNioFile(hostOwnedPath)
      })
      TrustedFiles.markExternallyOpened(hostOwnedFile)

      val provider = UntrustedFileNotificationProvider()
      assertNotNull(provider.collectNotificationData(host, file))
      assertNull(provider.collectNotificationData(host, internalFile))
      assertNull(provider.collectNotificationData(host, hostOwnedFile))

      // trusting the exact file location removes the banner but does not affect siblings
      val siblingFile = outsideFile.parent.resolve("sibling.txt")
      Files.writeString(siblingFile, "text")
      val sibling = requireNotNull(LocalFileSystem.getInstance().refreshAndFindFileByNioFile(siblingFile))
      TrustedFiles.markExternallyOpened(sibling)

      TrustedProjects.setProjectTrusted(outsideFile, true)
      assertNull(provider.collectNotificationData(host, file))
      assertNotNull(provider.collectNotificationData(host, sibling))
    }

  @Test
  fun `in an untrusted project a standalone file gets the file banner instead of the project banner`(): Unit =
    timeoutRunBlocking(context = Dispatchers.UiWithModelAccess) {
      Registry.get(TrustedFiles.SAFE_MODE_REGISTRY_KEY).setValue(true, asDisposable())
      val host = hostFixture.get()

      // an explicit trust state in TrustedPaths outlives the fixture, so restore the snapshot at the end
      val trustedPathsSnapshot = TrustedPaths.getInstance().state
      try {
        TrustedProjects.setProjectTrusted(host, false)

        val outsideFile = tempPath.resolve("outside").resolve("data.txt")
        Files.createDirectories(outsideFile.parent)
        Files.writeString(outsideFile, "text")
        val file = requireNotNull(LocalFileSystem.getInstance().refreshAndFindFileByNioFile(outsideFile))
        TrustedFiles.markExternallyOpened(file)

        // an unmarked outside file follows the project-level trust
        val unmarkedPath = tempPath.resolve("outside").resolve("unmarked.txt")
        Files.writeString(unmarkedPath, "text")
        val unmarkedFile = requireNotNull(LocalFileSystem.getInstance().refreshAndFindFileByNioFile(unmarkedPath))

        val insidePath = Path.of(host.basePath!!).resolve("inside.txt")
        Files.writeString(insidePath, "text")
        // refreshing under an open project's root fires VFS events synchronously and needs the write-intent lock
        val insideFile = requireNotNull(writeIntentReadAction {
          LocalFileSystem.getInstance().refreshAndFindFileByNioFile(insidePath)
        })
        TrustedFiles.markExternallyOpened(insideFile)

        val fileProvider = UntrustedFileNotificationProvider()
        val projectProvider = UntrustedProjectNotificationProvider()

        // the marked outside file gets the file banner, not the project banner
        assertNotNull(fileProvider.collectNotificationData(host, file))
        assertNull(projectProvider.collectNotificationData(host, file))

        // the inside file and the unmarked outside file keep the project banner
        assertNull(fileProvider.collectNotificationData(host, insideFile))
        assertNotNull(projectProvider.collectNotificationData(host, insideFile))
        assertNull(fileProvider.collectNotificationData(host, unmarkedFile))
        assertNotNull(projectProvider.collectNotificationData(host, unmarkedFile))

        // a trusted file location removes both banners for the file; the inside file keeps the project banner
        TrustedProjects.setProjectTrusted(outsideFile, true)
        assertNull(fileProvider.collectNotificationData(host, file))
        assertNull(projectProvider.collectNotificationData(host, file))
        assertNotNull(projectProvider.collectNotificationData(host, insideFile))
      }
      finally {
        TrustedPaths.getInstance().loadState(trustedPathsSnapshot)
      }
    }
}
