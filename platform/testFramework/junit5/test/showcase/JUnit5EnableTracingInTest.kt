// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework.junit5.showcase

import com.intellij.openapi.diagnostic.Logger
import com.intellij.testFramework.junit5.EnableTracingFor
import org.junit.jupiter.api.Test

// MyBusinessLogic logger works with the TRACE level inside the test run.
@EnableTracingFor(categoryClasses = [MyBusinessLogic::class])
class JUnit5EnableTracingInTest {

  @Test
  fun `traces are written to log file`() {
    MyBusinessLogic().run()
  }
}


private class MyBusinessLogic {
  private val LOG = Logger.getInstance(MyBusinessLogic::class.java)

  fun run() {
    LOG.trace("MyBusinessLogic#run started")

    // doing stuff  ...

    LOG.info("MyBusinessLogic#run finished")
  }
}