// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.testframework.sm.runner

import com.intellij.execution.testframework.sm.runner.ui.MockPrinter
import com.intellij.openapi.util.Disposer

class OutputTest : BaseSMTRunnerTestCase() {
  fun testBeforeAfterOrder() {
    val suite = createTestProxy("parent")
    suite.setTreeBuildBeforeStart()
    val child = createTestProxy("child", suite)
    child.setTreeBuildBeforeStart()

    suite.addStdOutput("before test started\n")
    child.setStarted()
    child.addStdOutput("inside test\n")
    child.setFinished()
    suite.addStdOutput("after test finished\n")

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

    suite.addStdOutput("before test started\n")
    child.setStarted()
    child.addStdOutput("inside test\n")
    child.setTestFailed("fail", null, false)
    suite.addStdOutput("after test finished\n")

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
      suite.addStdOutput("before test started\n")
      child.setStarted()
      child.addStdOutput("inside test\n")
      child.setFinished()
      suite.flush()
      suite.addStdOutput("after test finished\n")

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