// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.trustedProjects

import com.intellij.ide.TrustedFiles
import com.intellij.ide.impl.TrustedPaths
import com.intellij.ide.impl.TrustedPathsSettings
import com.intellij.ide.trustedProjects.TrustedProjectsLocator.LocatedProject
import com.intellij.ide.trustedProjects.impl.TrustedFileDialog
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.SystemProperty
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import com.intellij.util.ThreeState
import com.intellij.util.application
import com.intellij.util.asDisposable
import kotlinx.coroutines.CoroutineScope
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CopyOnWriteArrayList

@TestApplication
@SystemProperty("idea.trust.headless.disabled", "false")
class TrustedFileDialogTest {
  private val projectFixture = projectFixture(openAfterCreation = true)
  private val tempPath by tempPathFixture()
  private lateinit var savedTrustedLocations: List<String>
  private lateinit var savedExplicitlyTrustedPaths: List<String>

  @BeforeEach
  fun setUp() {
    savedTrustedLocations = TrustedPathsSettings.getInstance().getTrustedPaths()
    savedExplicitlyTrustedPaths = TrustedPaths.getInstance().getExplicitlyTrustedPaths()
  }

  @AfterEach
  fun tearDown() {
    // the mark store and the trust storages are application-level and would leak into the next test
    ExternallyOpenedFiles.getInstance().loadState(ExternallyOpenedFiles.State())
    TrustedPathsSettings.getInstance().setTrustedPaths(savedTrustedLocations)
    TrustedPaths.getInstance().setExplicitlyTrustedPaths(savedExplicitlyTrustedPaths)
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
    val file = requireNotNull(VirtualFileManager.getInstance().refreshAndFindFileByNioPath(outsidePath))
    val sibling = requireNotNull(VirtualFileManager.getInstance().refreshAndFindFileByNioPath(siblingPath))
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
    val file = requireNotNull(VirtualFileManager.getInstance().refreshAndFindFileByNioPath(outsidePath))
    val sibling = requireNotNull(VirtualFileManager.getInstance().refreshAndFindFileByNioPath(siblingPath))
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
    val file = requireNotNull(VirtualFileManager.getInstance().refreshAndFindFileByNioPath(outsidePath))
    TrustedFiles.markExternallyOpened(file)

    TrustedFileDialog.setDialogChoiceInTests(TrustedFileDialog.DialogChoice(isTrusted = false, isTrustFolder = false), asDisposable())
    assertFalse(TrustedProjectsDialog.confirmTrustingUntrustedFile(project, outsidePath))

    assertEquals(ThreeState.UNSURE, TrustedProjects.getProjectTrustedState(outsidePath))
    assertFalse(TrustedFiles.isTrusted(file, project))
  }

  @Test
  fun `trusting the folder records no grant for the file`(): Unit = timeoutRunBlocking {
    Registry.get(TrustedFiles.SAFE_MODE_REGISTRY_KEY).setValue(true, asDisposable())
    val project = projectFixture.get()
    val outsidePath = createMarkedFile("outside")

    TrustedFileDialog.setDialogChoiceInTests(TrustedFileDialog.DialogChoice(isTrusted = true, isTrustFolder = true), asDisposable())
    assertTrue(TrustedProjectsDialog.confirmTrustingUntrustedFile(project, outsidePath))

    assertFalse(TrustedPaths.getInstance().getExplicitlyTrustedPaths().contains(outsidePath.toString()))
    val locatedFile = TrustedProjectsLocator.locateProject(outsidePath, project = null)
    assertEquals(ThreeState.UNSURE, TrustedPaths.getInstance().getProjectTrustedState(locatedFile))
    // the trusted folder gives the trust to the file
    assertEquals(ThreeState.YES, TrustedProjects.getProjectTrustedState(outsidePath))
  }

  @Test
  fun `removing the trusted folder makes the file unsure again`(): Unit = timeoutRunBlocking {
    Registry.get(TrustedFiles.SAFE_MODE_REGISTRY_KEY).setValue(true, asDisposable())
    val project = projectFixture.get()
    val outsidePath = createMarkedFile("outside")
    val dir = outsidePath.parent

    TrustedFileDialog.setDialogChoiceInTests(TrustedFileDialog.DialogChoice(isTrusted = true, isTrustFolder = true), asDisposable())
    assertTrue(TrustedProjectsDialog.confirmTrustingUntrustedFile(project, outsidePath))
    assertEquals(ThreeState.YES, TrustedProjects.getProjectTrustedState(outsidePath))

    val settings = TrustedPathsSettings.getInstance()
    settings.setTrustedPaths(settings.getTrustedPaths() - dir.toString())

    assertEquals(ThreeState.UNSURE, TrustedProjects.getProjectTrustedState(outsidePath))
  }

  @Test
  fun `trusting the folder publishes one trust event for the folder`(): Unit = timeoutRunBlocking {
    Registry.get(TrustedFiles.SAFE_MODE_REGISTRY_KEY).setValue(true, asDisposable())
    val project = projectFixture.get()
    val outsidePath = createMarkedFile("outside")
    val trustEvents = collectTrustEvents()

    TrustedFileDialog.setDialogChoiceInTests(TrustedFileDialog.DialogChoice(isTrusted = true, isTrustFolder = true), asDisposable())
    assertTrue(TrustedProjectsDialog.confirmTrustingUntrustedFile(project, outsidePath))

    assertEquals(listOf(listOf(outsidePath.parent)), trustEvents.map { it.projectRoots })
  }

  @Test
  fun `trusting the file publishes one trust event for the file`(): Unit = timeoutRunBlocking {
    Registry.get(TrustedFiles.SAFE_MODE_REGISTRY_KEY).setValue(true, asDisposable())
    val project = projectFixture.get()
    val outsidePath = createMarkedFile("outside")
    val trustEvents = collectTrustEvents()

    TrustedFileDialog.setDialogChoiceInTests(TrustedFileDialog.DialogChoice(isTrusted = true, isTrustFolder = false), asDisposable())
    assertTrue(TrustedProjectsDialog.confirmTrustingUntrustedFile(project, outsidePath))

    assertEquals(listOf(listOf(outsidePath)), trustEvents.map { it.projectRoots })
    assertTrue(TrustedPaths.getInstance().getExplicitlyTrustedPaths().contains(outsidePath.toString()))
  }

  private fun createMarkedFile(dirName: String): Path {
    val dir = tempPath.resolve(dirName)
    Files.createDirectories(dir)
    val outsidePath = dir.resolve("data.txt")
    Files.writeString(outsidePath, "text")
    val file = requireNotNull(VirtualFileManager.getInstance().refreshAndFindFileByNioPath(outsidePath))
    TrustedFiles.markExternallyOpened(file)
    return outsidePath
  }

  private fun CoroutineScope.collectTrustEvents(): List<LocatedProject> {
    val trustEvents = CopyOnWriteArrayList<LocatedProject>()
    application.messageBus.connect(asDisposable()).subscribe(TrustedProjectsListener.TOPIC, object : TrustedProjectsListener {
      override fun onProjectTrusted(locatedProject: LocatedProject) {
        trustEvents.add(locatedProject)
      }
    })
    return trustEvents
  }
}
