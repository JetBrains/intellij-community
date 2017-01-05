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
package com.intellij.execution.testframework.sm.runner

import com.intellij.execution.process.ProcessOutputTypes
import com.intellij.execution.testframework.sm.runner.ui.MockPrinter
import com.intellij.openapi.util.Disposer

class OutputTest : BaseSMTRunnerTestCase() {
  fun testBeforeAfterOrder() {
    val suite = createTestProxy("parent")
    suite.setTreeBuildBeforeStart()
    val child = createTestProxy("child", suite)
    child.setTreeBuildBeforeStart()

    suite.addStdOutput("before test started\n", ProcessOutputTypes.STDOUT)
    child.setStarted()
    child.addStdOutput("inside test\n", ProcessOutputTypes.STDOUT)
    child.setFinished()
    suite.addStdOutput("after test finished\n", ProcessOutputTypes.STDOUT)

    val printer = MockPrinter(true)
    suite.printOn(printer)

    assertEquals("before test started\ninside test\nafter test finished\n", printer.stdOut)
    printer.resetIfNecessary()

    child.printOn(printer)
    assertEquals("inside test\n", printer.stdOut)
  }

  fun testBeforeAfterFailedOrder() {
    val suite = createTestProxy("parent")
    suite.setTreeBuildBeforeStart()
    val child = createTestProxy("child", suite)
    child.setTreeBuildBeforeStart()

    suite.addStdOutput("before test started\n", ProcessOutputTypes.STDOUT)
    child.setStarted()
    child.addStdOutput("inside test\n", ProcessOutputTypes.STDOUT)
    child.setTestFailed("fail", null, false)
    suite.addStdOutput("after test finished\n", ProcessOutputTypes.STDOUT)

    val printer = MockPrinter(true)
    suite.printOn(printer)

    assertEquals("before test started\ninside test\nafter test finished\n", printer.stdOut)
    printer.resetIfNecessary()

    child.printOn(printer)
    assertEquals("inside test\n", printer.stdOut)
  }

  fun testBeforeAfterOrderWhenFlushed() {
    val suite = createTestProxy("parent")
    suite.setTreeBuildBeforeStart()
    val child = createTestProxy("child", suite)
    child.setTreeBuildBeforeStart()

    try {
      suite.addStdOutput("before test started\n", ProcessOutputTypes.STDOUT)
      child.setStarted()
      child.addStdOutput("inside test\n", ProcessOutputTypes.STDOUT)
      child.setFinished()
      suite.flush()
      suite.addStdOutput("after test finished\n", ProcessOutputTypes.STDOUT)

      val printer = MockPrinter(true)
      suite.printOn(printer)

      assertEquals("before test started\ninside test\nafter test finished\n", printer.stdOut)
      printer.resetIfNecessary()

      child.printOn(printer)
      assertEquals("inside test\n", printer.stdOut)
    }
    finally {
      Disposer.dispose(child)
      Disposer.dispose(suite)
    }
  }
}