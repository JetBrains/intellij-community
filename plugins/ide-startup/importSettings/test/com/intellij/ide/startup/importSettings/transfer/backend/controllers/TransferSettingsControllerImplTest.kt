// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.startup.importSettings.transfer.backend.controllers

import com.intellij.ide.startup.importSettings.controllers.TransferSettingsControllerImpl
import com.intellij.ide.startup.importSettings.models.Settings
import com.intellij.ide.startup.importSettings.transfer.backend.providers.KeymapSchemeImportPerformer
import com.intellij.ide.startup.importSettings.transfer.backend.providers.LookAndFeelImportPerformer
import com.intellij.ide.startup.importSettings.transfer.backend.providers.PartialImportPerformer
import com.intellij.ide.startup.importSettings.transfer.backend.providers.RecentProjectsImportPerformer
import com.intellij.ide.startup.importSettings.transfer.backend.providers.SyntaxSchemeImportPerformer
import com.intellij.ide.startup.importSettings.transfer.backend.providers.TestPartialImportPerformer
import com.intellij.openapi.Disposable
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@TestApplication
class TransferSettingsControllerImplTest {
  @TestDisposable
  lateinit var disposable: Disposable

  @Test
  fun `built-in partial performers are registered for the extension point`() {
    val registered = PartialImportPerformer.EP_NAME.extensionList.map { it.javaClass }

    val builtIn = listOf(
      LookAndFeelImportPerformer::class.java,
      SyntaxSchemeImportPerformer::class.java,
      KeymapSchemeImportPerformer::class.java,
      RecentProjectsImportPerformer::class.java,
    )
    assertTrue(registered.containsAll(builtIn), "Registered partial performers: $registered")
  }

  @Test
  fun `import performer runs the partial performers from the extension point`() {
    val partial = TestPartialImportPerformer(willPerform = true)
    ExtensionTestUtil.maskExtensions(PartialImportPerformer.EP_NAME, listOf(partial), disposable)
    val settings = Settings()

    TransferSettingsControllerImpl().getImportPerformer().performEdt(null, settings)

    assertEquals(listOf(settings), partial.performEdtCalls)
  }
}
