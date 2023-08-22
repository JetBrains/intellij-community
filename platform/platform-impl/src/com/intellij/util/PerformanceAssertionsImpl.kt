// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util

import com.intellij.codeInsight.daemon.impl.GeneralHighlightingPass
import com.intellij.psi.impl.source.resolve.reference.ReferenceProvidersRegistry

internal class PerformanceAssertionsImpl: PerformanceAssertions() {
  override fun checkDoesNotAffectHighlighting() {
    GeneralHighlightingPass.assertHighlightingPassNotRunning()
    ReferenceProvidersRegistry.assertNotContributingReferences()
  }
}