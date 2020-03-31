// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testFramework

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.text.StringUtil
import org.junit.Rule
import org.junit.internal.AssumptionViolatedException
import org.junit.rules.TestRule
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.junit.runners.model.Statement
import java.util.concurrent.atomic.AtomicReference

@RunWith(JUnit4::class)
abstract class LightPlatform4TestCase : LightPlatformTestCase() {
  @Rule
  @JvmField
  var rule: TestRule = TestRule { base, description ->
    object : Statement() {
      override fun evaluate() {
        TestRunnerUtil.replaceIdeEventQueueSafely()
        val name = description.methodName
        setName(if (name.startsWith("test")) name else "test" + StringUtil.capitalize(name))

        setUp()

        /*
         * Allows to throw the original exceptions rather than them being wrapped into a RuntimeException.
         */
        val failedAssumption: AtomicReference<AssumptionViolatedException?> = AtomicReference()
        val failure: AtomicReference<Throwable?> = AtomicReference()

        ApplicationManager.getApplication().invokeAndWait {
          try {
            base.evaluate()
          }
          catch (ave: AssumptionViolatedException) {
            failedAssumption.set(ave)
          }
          catch (t: Throwable) {
            /*
             * This is either a failed assertion (AssertionError) or an unexpected failure.
             */
            failure.set(t)
          }
          finally {
            try {
              tearDown()
            } catch (tearDownFailure: Throwable) {
              when (val firstFailure = failure.get()) {
                null -> failure.set(tearDownFailure)
                else -> firstFailure.addSuppressed(tearDownFailure)
              }
            }
          }
        }

        /*
         * 1. Throw a failure, if any.
         */
        failure.get()?.let { throw it }

        /*
         * 2. Throw a failed assumption, if any.
         */
        failedAssumption.get()?.let { throw it }
      }
    }
  }
}
