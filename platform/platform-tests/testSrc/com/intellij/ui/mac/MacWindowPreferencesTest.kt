package com.intellij.ui.mac

import org.assertj.core.api.Assertions.assertThat

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledOnOs
import org.junit.jupiter.api.condition.OS
import java.util.UUID

@EnabledOnOs(OS.MAC)
internal class MacWindowPreferencesTest {
  @Test
  fun `missing preference returns null`() {
    assertThat(MacWindowPreferences.readString(UUID.randomUUID().toString(), "com.jetbrains.intellij.tests")).isNull()
  }
}
