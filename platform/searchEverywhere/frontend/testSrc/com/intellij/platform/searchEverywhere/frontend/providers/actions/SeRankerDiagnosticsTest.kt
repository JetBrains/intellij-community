// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.frontend.providers.actions

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SeRankerDiagnosticsTest {
  @Test
  fun `the diagnostic text shows the ranker and its probability`() {
    assertEquals("Jev p=0.734", rankerDiagnostics("Jev", SeActionsRankerMatch("ZoomIn", 0.7341)))
  }

  @Test
  fun `the diagnostic text of a match without a probability shows only the ranker`() {
    assertEquals("mock", rankerDiagnostics("mock", SeActionsRankerMatch("ZoomIn", probability = null)))
  }

  @Test
  fun `the rankers name their remote models`() {
    assertEquals("Decision API", SeDecisionsActionsRanker().diagnosticName)
    assertEquals("Jev", SeJevActionsRanker().diagnosticName)
    assertEquals("mock", SeMockActionsRanker().diagnosticName)
  }
}
