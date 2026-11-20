// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework.junit5

import com.intellij.testFramework.junit5.impl.IdeAnalysisHostRuntime
import org.jetbrains.annotations.TestOnly
import org.junit.jupiter.api.extension.Extension
import java.util.ServiceLoader

/**
 * Runs the tests of an [AnalysisHostTest] class on one [AnalysisHostTest.Host].
 *
 * The classpath of the test target selects the runtime. The runtime is the first provider that [ServiceLoader] finds.
 * Without a provider, the runtime is the IDE test application.
 * A host that cannot share the JVM with the IDE application needs its own test target.
 * The language server is such a host.
 *
 * A language adds its fixtures in its own service, and selects the implementation that matches [host].
 */
@TestOnly
interface AnalysisHostRuntime {
  val host: AnalysisHostTest.Host

  /**
   * The JUnit extensions of the runtime. [AnalysisHostTest] calls them for each test class.
   * JUnit calls the "before" callbacks in the order of the list, and the "after" callbacks in the reverse order.
   * An extension can implement only `BeforeAllCallback`, `AfterAllCallback`, `BeforeEachCallback` and `AfterEachCallback`.
   * [AnalysisHostTest] fails a test class when an extension implements another JUnit interface.
   */
  val extensions: List<Extension>

  companion object {
    private val current: AnalysisHostRuntime by lazy {
      ServiceLoader.load(AnalysisHostRuntime::class.java, AnalysisHostRuntime::class.java.classLoader).firstOrNull()
      ?: IdeAnalysisHostRuntime
    }

    @JvmStatic
    fun current(): AnalysisHostRuntime = current
  }
}
