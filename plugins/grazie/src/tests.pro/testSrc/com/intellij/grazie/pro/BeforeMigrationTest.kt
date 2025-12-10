package com.intellij.grazie.pro

import org.junit.Test

internal class BeforeMigrationTest {

  @Test
  fun alwaysTrueTest() {
    assert(true) { "Before the migration failed" }
  }
}
