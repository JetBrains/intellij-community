// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.settings.v2

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

/**
 * Checks that the internal action that dumps the settings context takes its text from the shared settings bundle.
 * The descriptor of `intellij.platform.settings.v2.backend` points its actions at `messages.SettingsV2Bundle`.
 */
@TestApplication
class SettingsContextDumpActionTest {
  @Test
  fun `the dump action takes its text from the bundle`() {
    val action = ActionManager.getInstance().getAction("internal.settings.v2.DumpSettingsContext")
    assertNotNull(action)
    assertEquals("Dump Settings Context", action!!.templatePresentation.text)
  }
}
