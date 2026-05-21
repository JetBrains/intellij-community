// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compose.ide.plugin.resources

import com.intellij.compose.ide.plugin.resources.vectorDrawable.preview.ComposeResourceEditorProvider
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class ComposeResourceEditorProviderTest : BasePlatformTestCase() {
  private val provider = ComposeResourceEditorProvider()

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

  fun `test rejects file without parent`() {
    assertFalse(isAccepted("vector.xml"))
  }

  private fun isAccepted(path: String): Boolean {
    val file = myFixture.addFileToProject(path, "").virtualFile
    return provider.accept(project, file)
  }
}