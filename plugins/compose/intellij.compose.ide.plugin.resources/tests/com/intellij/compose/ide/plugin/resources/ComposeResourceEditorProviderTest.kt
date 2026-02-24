// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compose.ide.plugin.resources

import com.intellij.compose.ide.plugin.resources.previewDrawables.ComposeResourceEditorProvider
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class ComposeResourceEditorProviderTest : BasePlatformTestCase() {
  private val provider = ComposeResourceEditorProvider()

  fun `test accepts xml file in drawable under composeResources`() {
    assertTrue(isAccepted("$COMPOSE_RESOURCES_DIR/drawable/compose-multiplatform.xml"))
  }

  fun `test rejects xml file not in composeResources`() {
    assertFalse(isAccepted("res/drawable/compose-multiplatform.xml"))
  }

  fun `test rejects strings xml`() {
    assertFalse(isAccepted("$COMPOSE_RESOURCES_DIR/drawable/$STRINGS_XML_FILENAME"))
  }

  fun `test rejects non-xml file`() {
    assertFalse(isAccepted("$COMPOSE_RESOURCES_DIR/drawable/image.png"))
  }

  fun `test rejects xml in values folder`() {
    assertFalse(isAccepted("$COMPOSE_RESOURCES_DIR/values/colors.xml"))
  }

  private fun isAccepted(path: String): Boolean {
    val file = myFixture.addFileToProject(path, "").virtualFile
    return provider.accept(project, file)
  }
}