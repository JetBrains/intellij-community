// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.frontend.providers.actions

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class SeRankerDiagnosticsTest {
  @Test
  fun `the diagnostic text shows the probability of the ranker`() {
    assertEquals("p=0.734", rankerDiagnostics(SeActionsRankerMatch("ZoomIn", 0.7341)))
  }

  @Test
  fun `a match without a probability has no diagnostic text`() {
    assertNull(rankerDiagnostics(SeActionsRankerMatch("ZoomIn", probability = null)))
  }
}
