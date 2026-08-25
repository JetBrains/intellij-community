// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.trustedProjects

import com.intellij.ide.impl.TrustedPaths
import com.intellij.openapi.application.UiWithModelAccess
import com.intellij.openapi.application.writeIntentReadAction
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.SystemProperty
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import com.intellij.util.ThreeState
import com.intellij.util.asDisposable
import kotlinx.coroutines.Dispatchers
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

  @Test
  fun `every file is trusted while the safe mode is off`(): Unit = timeoutRunBlocking {
    val project = projectFixture.get()
    val outsideFile = tempPath.resolve("outside.txt")
    Files.writeString(outsideFile, "text")
    val file = requireNotNull(LocalFileSystem.getInstance().refreshAndFindFileByNioFile(outsideFile))

    // the registry key is off by default
    assertTrue(TrustedFiles.isTrusted(file, project))
  }

  @Test
  fun `outside files are untrusted until their location is trusted`(): Unit =
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

      // a file inside the project's own roots is trusted
      val insidePath = Path.of(project.basePath!!).resolve("inside.txt")
      Files.writeString(insidePath, "text")
      // refreshing under an open project's root fires VFS events synchronously and needs the write-intent lock
      val inside = requireNotNull(writeIntentReadAction {
        LocalFileSystem.getInstance().refreshAndFindFileByNioFile(insidePath)
      })
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
