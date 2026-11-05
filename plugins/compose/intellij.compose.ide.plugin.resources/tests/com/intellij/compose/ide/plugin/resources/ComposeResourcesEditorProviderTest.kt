// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compose.ide.plugin.resources

import com.intellij.compose.ide.plugin.resources.vectorDrawable.preview.ComposeResourceEditorProvider
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiFile
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.nio.file.Path

class ComposeResourcesEditorProviderTest : BasePlatformTestCase() {
  private val provider = ComposeResourceEditorProvider()
  private val dataProvider = FakeComposeResourcesDataProvider()

  override fun setUp() {
    super.setUp()
    ExtensionTestUtil.maskExtensions(ComposeResourcesDataProvider.EP_NAME, listOf(dataProvider), testRootDisposable)
  }

  fun `test accepts xml in drawable folder`() {
    assertTrue(isAccepted("$COMPOSE_RESOURCES_DIR/drawable/vector.xml"))
  }

  fun `test accepts xml in drawable-xxhdpi folder`() {
    assertTrue(isAccepted("$COMPOSE_RESOURCES_DIR/drawable-xxhdpi/vector.xml"))
  }

  fun `test rejects non-xml file`() {
    assertFalse(isAccepted("$COMPOSE_RESOURCES_DIR/drawable/image.png"))
  }

  fun `test rejects xml not in drawable folder`() {
    assertFalse(isAccepted("$COMPOSE_RESOURCES_DIR/values/colors.xml"))
  }

  fun `test rejects xml at project root`() {
    assertFalse(isAccepted("vector.xml"))
  }

  fun `test rejects drawable xml outside Compose resources folder`() {
    assertFalse(isAccepted("res/drawable/vector.xml"))
  }

  fun `test rejects drawable xml without applicable provider`() {
    dataProvider.isApplicable = false
    assertFalse(isAccepted("$COMPOSE_RESOURCES_DIR/drawable/vector.xml"))
  }

  private fun isAccepted(path: String): Boolean {
    val file = myFixture.addFileToProject(path, "").virtualFile
    return provider.accept(project, file)
  }

  /** Reports direct subfolders of [COMPOSE_RESOURCES_DIR] as Compose resources folders. */
  private class FakeComposeResourcesDataProvider : ComposeResourcesDataProvider {
    var isApplicable: Boolean = true

    override fun isApplicable(project: Project): Boolean = isApplicable
    override fun getComposeDataForModule(module: Module): ComposeResourcesData? = null
    override fun getComposeDataForResourceFile(file: PsiFile): ComposeResourcesData? = null
    override fun getComposeDataForResourceFolder(folder: PsiDirectory): ComposeResourcesData? =
      FakeComposeResourcesData.takeIf { folder.virtualFile.parent?.name == COMPOSE_RESOURCES_DIR }
  }

  private object FakeComposeResourcesData : ComposeResourcesData {
    override val isPublicResClass: Boolean = false
    override val nameOfResClass: String = "Res"
    override val packageOfResClass: String = "test"
    override val directoryPath: Path = Path.of(COMPOSE_RESOURCES_DIR)
    override val commonResourcesPath: Path? = null
    override val isCustomDirectory: Boolean = false
    override val accessorsDirectory: VirtualFile? = null
    override val accessorsQualifier: String = "commonMain"
  }
}
