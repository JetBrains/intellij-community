// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.application.constraints

import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.LightPlatformTestCase
import kotlinx.coroutines.Job

/**
 * @author eldar
 */
class ExpirationTest : LightPlatformTestCase() {

  private fun createTestDisposable(): Disposable {
    return Disposer.newDisposable(name).also { Disposer.register(testRootDisposable, it) }
  }

  private fun createCompositeExpiration(vararg expiration: Expiration): Expiration {
    assertTrue(expiration.isNotEmpty())
    return Expiration.composeExpiration(expiration.toList())!!
  }

  fun `test isExpired for already disposed Disposable`() {
    val disposable = createTestDisposable()
    Disposer.dispose(disposable)
    val expiration = DisposableExpiration(disposable)
    assertTrue("Expiration.isExpired must be true", expiration.isExpired)
  }

  fun `test isExpired for JobExpiration`() {
    val job = Job()
    val expiration = JobExpiration(job)
    doTestExpiration(expiration) {
      job.cancel()
    }
  }

  fun `test isExpired for DisposableExpiration`() {
    val disposable = createTestDisposable()
    val expiration = DisposableExpiration(disposable)
    doTestExpiration(expiration) {
      Disposer.dispose(disposable)
    }
  }

  fun `test isExpired for composite JobExpiration`() {
    val job = Job()
    val disposable = createTestDisposable()
    val compositeExpiration = createCompositeExpiration(JobExpiration(job),
                                                        DisposableExpiration(disposable))
    doTestExpiration(compositeExpiration) {
      job.cancel()
    }
  }

  fun `test isExpired for composite DisposableExpiration`() {
    val job = Job()
    val disposable = createTestDisposable()
    val compositeExpiration = createCompositeExpiration(JobExpiration(job),
                                                        DisposableExpiration(disposable))
    doTestExpiration(compositeExpiration) {
      Disposer.dispose(disposable)
    }
  }

  private fun doTestExpiration(expiration: Expiration, expire: () -> Unit) {
    assertFalse(expiration.isExpired)

    var wasHandlerCalled = false
    expiration.invokeOnExpiration {
      assertFalse("Expiration.isExpired must be false inside the first handler", expiration.isExpired)
      wasHandlerCalled = true
    }
    assertFalse("Expiration handler must have not been called until expiration", wasHandlerCalled)

    expire()

    assertTrue("Expiration.isExpired must be true", expiration.isExpired)
    assertTrue("Expiration handler must have been called", wasHandlerCalled)

    var wasHandlerCalledAgain = false
    expiration.invokeOnExpiration {
      assertTrue("Expiration.isExpired must be true inside the second handler", expiration.isExpired)
      wasHandlerCalledAgain = true
    }
    assertTrue("Expiration handler must have been called immediately", wasHandlerCalledAgain)
  }
}