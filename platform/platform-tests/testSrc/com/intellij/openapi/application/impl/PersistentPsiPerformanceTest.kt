// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application.impl

import com.intellij.concurrency.installThreadContext
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ex.ApplicationManagerEx
import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.psi.impl.source.tree.mvcc.InternalPsiVersioning.PsiVersioningLockingListener
import com.intellij.psi.impl.source.tree.mvcc.InternalPsiVersioning.getCurrentPsiVersion
import com.intellij.psi.util.PsiVersioningService
import com.intellij.testFramework.junit5.StressTestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.tools.ide.metrics.benchmark.Benchmark
import org.junit.jupiter.api.Test
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

@StressTestApplication
internal class PersistentPsiPerformanceTest {

  class ElementA: AbstractCoroutineContextElement(Key) {
    object Key : CoroutineContext.Key<ElementA>
  }

  class ElementB: AbstractCoroutineContextElement(Key) {
    object Key : CoroutineContext.Key<ElementB>
  }

  class ElementC: AbstractCoroutineContextElement(Key) {
    object Key : CoroutineContext.Key<ElementC>
  }

  class ElementD: AbstractCoroutineContextElement(Key) {
    object Key : CoroutineContext.Key<ElementD>
  }

  class ElementE: AbstractCoroutineContextElement(Key) {
    object Key : CoroutineContext.Key<ElementE>
  }

  class ElementF: AbstractCoroutineContextElement(Key) {
    object Key : CoroutineContext.Key<ElementF>
  }

  class ElementG: AbstractCoroutineContextElement(Key) {
    object Key : CoroutineContext.Key<ElementG>
  }

  class ElementH: AbstractCoroutineContextElement(Key) {
    object Key : CoroutineContext.Key<ElementH>
  }

  @Test
  fun `getCurrentPsiVersion performance test`() {
    // we inflate thread context to simulate real ide context side
    installThreadContext(ElementA() + ElementB() + ElementC() + ElementD() + ElementE() + ElementF() + ElementG() + ElementH()) {
      Benchmark.newBenchmark("getCurrentPsiVersionPerformanceTest") {
        PsiVersioningService.freezePsiVersion {
          repeat(10_000_000) {
            getCurrentPsiVersion()
          }
        }
      }.warmupIterations(5)
        .attempts(5)
        .start()
    }
  }

  @Test
  fun `getCurrentPsiVersion in read action performance test `(@TestDisposable testDisposable: Disposable) {

    ApplicationManagerEx.getApplicationEx().addWriteActionListener(PsiVersioningLockingListener(), testDisposable)
    ApplicationManagerEx.getApplicationEx().addReadActionListener(PsiVersioningLockingListener(), testDisposable)
    ApplicationManagerEx.getApplicationEx().addWriteIntentReadActionListener(PsiVersioningLockingListener(), testDisposable)
    // we inflate thread context to simulate real ide context side
    installThreadContext(ElementA() + ElementB() + ElementC() + ElementD() + ElementE() + ElementF() + ElementG() + ElementH()) {
      Benchmark.newBenchmark("getCurrentPsiVersionPerformanceTest") {
        runReadActionBlocking {
          repeat(10_000_000) {
            getCurrentPsiVersion()
          }
        }
      }.warmupIterations(5)
        .attempts(5)
        .start()
    }
  }

}
