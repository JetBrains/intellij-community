// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.trustedProjects.impl

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

      // a file inside the host project's roots needs no banner
      val hostOwnedPath = Path.of(host.basePath!!).resolve("host-owned.txt")
      Files.writeString(hostOwnedPath, "text")
      // refreshing under an open project's root fires VFS events synchronously and needs the write-intent lock
      val hostOwnedFile = requireNotNull(writeIntentReadAction {
        LocalFileSystem.getInstance().refreshAndFindFileByNioFile(hostOwnedPath)
      })

      val provider = UntrustedFileNotificationProvider()
      assertNotNull(provider.collectNotificationData(host, file))
      assertNull(provider.collectNotificationData(host, hostOwnedFile))

      // trusting the exact file location removes the banner but does not affect siblings
      val siblingFile = outsideFile.parent.resolve("sibling.txt")
      Files.writeString(siblingFile, "text")
      val sibling = requireNotNull(LocalFileSystem.getInstance().refreshAndFindFileByNioFile(siblingFile))

      TrustedProjects.setProjectTrusted(outsideFile, true)
      assertNull(provider.collectNotificationData(host, file))
      assertNotNull(provider.collectNotificationData(host, sibling))
    }
}
