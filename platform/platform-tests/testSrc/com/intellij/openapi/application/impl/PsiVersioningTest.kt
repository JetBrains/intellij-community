// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application.impl

import com.intellij.psi.util.PsiVersioningService
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.util.concurrency.ThreadingAssertions
import org.junit.jupiter.api.Test

@TestApplication
internal class PsiVersioningTest {

  @Test
  fun `freezePsiVersion runs a read action`() {
    PsiVersioningService.freezePsiVersion {
      ThreadingAssertions.assertReadAccess()
    }
  }
}
