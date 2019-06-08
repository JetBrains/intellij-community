// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy

import com.intellij.testFramework.EdtTestUtil
import com.intellij.testFramework.LightProjectDescriptor
import groovy.transform.CompileStatic
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

import static org.jetbrains.plugins.groovy.GroovyProjectDescriptors.GROOVY_LATEST_REAL_JDK

@CompileStatic
class SetupRule implements TestRule {

  final LightGroovyTestCase testCase = new LightGroovyTestCase() {
    LightProjectDescriptor projectDescriptor = GROOVY_LATEST_REAL_JDK
  }

  @Override
  Statement apply(Statement base, Description description) {
    new Statement() {
      @Override
      void evaluate() throws Throwable {
        testCase.setUp()
        try {
          EdtTestUtil.runInEdtAndWait {
            base.evaluate()
          }
        }
        finally {
          testCase.tearDown()
        }
      }
    }
  }
}
