/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.plugins.groovy

import com.intellij.testFramework.EdtTestUtil
import com.intellij.testFramework.LightProjectDescriptor
import groovy.transform.CompileStatic
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

@CompileStatic
class SetupRule implements TestRule {

  final LightGroovyTestCase testCase = new LightGroovyTestCase() {
    LightProjectDescriptor projectDescriptor = GroovyLightProjectDescriptor.GROOVY_LATEST_REAL_JDK
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
