// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testFramework

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.text.StringUtil
import com.intellij.util.ExceptionUtil
import org.junit.Rule
import org.junit.internal.AssumptionViolatedException
import org.junit.rules.TestRule
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.junit.runners.model.Statement

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
        try {
          ApplicationManager.getApplication().invokeAndWait {
            try {
              base.evaluate()
            }
            finally {
              tearDown()
            }
          }
        }
        catch (e: Throwable) {
          // invokeAndWait() can wrap the real exception into RuntimeException.
          ExceptionUtil.findCause(e, AssumptionViolatedException::class.java)?.let { ave ->
            throw ave
          }
          throw e
        }
      }
    }
  }
}
