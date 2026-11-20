// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework.junit5.impl

import com.intellij.testFramework.junit5.AnalysisHostRuntime
import com.intellij.testFramework.junit5.AnalysisHostTest
import org.jetbrains.annotations.TestOnly
import org.junit.jupiter.api.extension.Extension

/**
 * The default [AnalysisHostRuntime]. It calls the extensions of [com.intellij.testFramework.junit5.TestApplication].
 */
@TestOnly
internal object IdeAnalysisHostRuntime : AnalysisHostRuntime {
  override val host: AnalysisHostTest.Host = AnalysisHostTest.Host.IDE

  override val extensions: List<Extension> = listOf(TestApplicationExtension(), TestApplicationLeakTrackerExtension())
}
