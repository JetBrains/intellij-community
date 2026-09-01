// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.trustedProjects

import com.intellij.ide.impl.TrustedPathsSettings
import com.intellij.ide.trustedProjects.impl.TrustedFileDialog
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.SystemProperty
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import com.intellij.util.ThreeState
import com.intellij.util.asDisposable
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files

@TestApplication
@SystemProperty("idea.trust.headless.disabled", "false")
class TrustedFileDialogTest {
  private val projectFixture = projectFixture(openAfterCreation = true)
  private val tempPath by tempPathFixture()

  @AfterEach
  fun tearDown() {
    // the mark store is application-level and would leak into the next test
    ExternallyOpenedFiles.getInstance().loadState(ExternallyOpenedFiles.State())
  }

  @Test
  fun `trusting the file covers only the file`(): Unit = timeoutRunBlocking {
    Registry.get(TrustedFiles.SAFE_MODE_REGISTRY_KEY).setValue(true, asDisposable())
    val project = projectFixture.get()

    val dir = tempPath.resolve("outside")
    Files.createDirectories(dir)
    val outsidePath = dir.resolve("data.txt")
    val siblingPath = dir.resolve("sibling.txt")
    Files.writeString(outsidePath, "text")
    Files.writeString(siblingPath, "text")
    val file = requireNotNull(LocalFileSystem.getInstance().refreshAndFindFileByNioFile(outsidePath))
    val sibling = requireNotNull(LocalFileSystem.getInstance().refreshAndFindFileByNioFile(siblingPath))
    TrustedFiles.markExternallyOpened(file)
    TrustedFiles.markExternallyOpened(sibling)
    assertFalse(TrustedFiles.isTrusted(file, project))
    assertFalse(TrustedFiles.isTrusted(sibling, project))

    TrustedFileDialog.setDialogChoiceInTests(TrustedFileDialog.DialogChoice(isTrusted = true, isTrustFolder = false), asDisposable())
    assertTrue(TrustedProjectsDialog.confirmTrustingUntrustedFile(project, outsidePath))

    assertEquals(ThreeState.YES, TrustedProjects.getProjectTrustedState(outsidePath))
    assertTrue(TrustedFiles.isTrusted(file, project))
    assertFalse(TrustedFiles.isTrusted(sibling, project))
    assertFalse(TrustedPathsSettings.getInstance().getTrustedPaths().contains(dir.toString()))
  }

  @Test
  fun `trusting the folder covers the sibling too`(): Unit = timeoutRunBlocking {
    Registry.get(TrustedFiles.SAFE_MODE_REGISTRY_KEY).setValue(true, asDisposable())
    val project = projectFixture.get()

    val dir = tempPath.resolve("outside")
    Files.createDirectories(dir)
    val outsidePath = dir.resolve("data.txt")
    val siblingPath = dir.resolve("sibling.txt")
    Files.writeString(outsidePath, "text")
    Files.writeString(siblingPath, "text")
    val file = requireNotNull(LocalFileSystem.getInstance().refreshAndFindFileByNioFile(outsidePath))
    val sibling = requireNotNull(LocalFileSystem.getInstance().refreshAndFindFileByNioFile(siblingPath))
    TrustedFiles.markExternallyOpened(file)
    TrustedFiles.markExternallyOpened(sibling)
    // cache both verdicts: the trust event fired by the confirmation must reset them
    assertFalse(TrustedFiles.isTrusted(file, project))
    assertFalse(TrustedFiles.isTrusted(sibling, project))

    TrustedFileDialog.setDialogChoiceInTests(TrustedFileDialog.DialogChoice(isTrusted = true, isTrustFolder = true), asDisposable())
    assertTrue(TrustedProjectsDialog.confirmTrustingUntrustedFile(project, outsidePath))

    assertTrue(TrustedPathsSettings.getInstance().getTrustedPaths().contains(dir.toString()))
    assertTrue(TrustedFiles.isTrusted(file, project))
    assertTrue(TrustedFiles.isTrusted(sibling, project))
  }

  @Test
  fun `staying in the safe mode keeps the state unsure`(): Unit = timeoutRunBlocking {
    Registry.get(TrustedFiles.SAFE_MODE_REGISTRY_KEY).setValue(true, asDisposable())
    val project = projectFixture.get()

    val outsidePath = tempPath.resolve("distrusted.txt")
    Files.writeString(outsidePath, "text")
    val file = requireNotNull(LocalFileSystem.getInstance().refreshAndFindFileByNioFile(outsidePath))
    TrustedFiles.markExternallyOpened(file)

    TrustedFileDialog.setDialogChoiceInTests(TrustedFileDialog.DialogChoice(isTrusted = false, isTrustFolder = false), asDisposable())
    assertFalse(TrustedProjectsDialog.confirmTrustingUntrustedFile(project, outsidePath))

    assertEquals(ThreeState.UNSURE, TrustedProjects.getProjectTrustedState(outsidePath))
    assertFalse(TrustedFiles.isTrusted(file, project))
  }
}
