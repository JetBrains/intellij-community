// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.updateSettings.impl

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MigrationKillSwitchTest {
  @Test
  fun `only true turns the migration off`() {
    assertTrue(isActive("true"))
    assertTrue(isActive("TRUE"))
    for (value in listOf(null, "", "false", "yes", "1")) {
      assertFalse(isActive(value), "value: ${value}")
    }
  }

  @Test
  fun `only the documented property turns the migration off`() {
    for (key in listOf("ide.migration.disabled", "ide.update.migration.disabled.x", "ide.no.platform.update")) {
      assertFalse(MigrationKillSwitch.isActive { if (it == key) "true" else null }, "key: ${key}")
    }
  }

  private fun isActive(value: String?): Boolean =
    MigrationKillSwitch.isActive { if (it == "ide.update.migration.disabled") value else null }
}
