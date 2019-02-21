// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.testframework.sm.runner

import com.intellij.execution.process.ProcessOutputTypes
import com.intellij.execution.testframework.sm.ServiceMessageBuilder
import com.intellij.testFramework.LightPlatformTestCase
import org.junit.Assert


class StdoutToSMTreeTest : BaseSMTRunnerTestCase() {
  private val converter: OutputToGeneralTestEventsConverter by lazy { OutputToGeneralTestEventsConverter("MyTest", false) }
  private val testProxy by lazy { SMTestProxy.SMRootTestProxy() }

  private var flushEachChar = 0


  private val suiteName = "suite_dummy"
  private val testFailName = "test_dummy"
  private val testSuccessName = "test_dummy_2"

  fun testIdBased() {
    flushEachChar = 0
    buildTestTree(idBased = true)
  }

  fun testIdBasedFlushEachChar() {
    flushEachChar = 1
    buildTestTree(idBased = true)
  }

  fun testIdBasedFlushEachFourChars() {
    flushEachChar = 4
    buildTestTree(idBased = true)
  }


  fun testGeneric() {
    flushEachChar = 0
    buildTestTree(idBased = false)
  }

  fun testGenericFlushEachChar() {
    flushEachChar = 1
    buildTestTree(idBased = false)
  }

  fun testGenericFlushEachFourChars() {
    flushEachChar = 4
    buildTestTree(idBased = false)
  }


  private fun buildTestTree(idBased: Boolean) {
    converter.processor = if (idBased) {
      GeneralIdBasedToSMTRunnerEventsConvertor(LightPlatformTestCase.ourProject, testProxy, "root")
    }
    else {
      GeneralToSMTRunnerEventsConvertor(LightPlatformTestCase.getProject(), testProxy, "root")
    }

    start()
    if (idBased) {
      idBasedTree()
    }
    else {
      genericTree()
    }
    finish()

    val failMessage = getTestOutput(findTestByName(testFailName, testProxy)!!)
    Assert.assertEquals("Wrong fail message", "failMessage", failMessage.trim())
    val tree = getFormattedTestTree(testProxy)
    val success = if (idBased) "+" else "-" // for id-based one must set state explicitly while in general state is based on children
    Assert.assertEquals("Wrong test tree", "Test tree:\n" +
                                           "[root]($success)\n" +
                                           ".${suiteName}($success)\n" +
                                           "..${testFailName}(-)\n" +
                                           "..${testSuccessName}(+)\n", tree)
  }

  private fun idBasedTree() {
    message(ServiceMessageBuilder.testStarted(suiteName), mapOf("nodeId" to "1", "parentNodeId" to "0"))

    message(ServiceMessageBuilder.testStarted(testFailName), mapOf("nodeId" to "2", "parentNodeId" to "1"))
    message(ServiceMessageBuilder.testFailed(testFailName), mapOf("nodeId" to "2", "message" to "failMessage"))

    message(ServiceMessageBuilder.testStarted(testSuccessName), mapOf("nodeId" to "3", "parentNodeId" to "1"))
    message(ServiceMessageBuilder.testFinished(testSuccessName), "nodeId", "3")

    message(ServiceMessageBuilder.testFinished(suiteName), "nodeId", "1")
  }

  private fun genericTree() {
    message(ServiceMessageBuilder.testSuiteStarted(suiteName))

    message(ServiceMessageBuilder.testStarted(testFailName))
    message(ServiceMessageBuilder.testFailed(testFailName), "message", "failMessage")

    message(ServiceMessageBuilder.testStarted(testSuccessName))
    message(ServiceMessageBuilder.testFinished(testSuccessName))

    message(ServiceMessageBuilder.testSuiteFinished(suiteName))
  }


  private fun finish() {
    testProxy.setFinished()
    converter.finishTesting()
  }

  private fun start() {
    converter.processor.onStartTesting()
    message(ServiceMessageBuilder("enteredTheMatrix"))
    message(ServiceMessageBuilder("testingStarted"))
  }

  private fun message(message: ServiceMessageBuilder, key: String, value: String) {
    message(message, mapOf(key to value))
  }

  private fun message(message: ServiceMessageBuilder, attrs: Map<String, String> = emptyMap()) {
    attrs.forEach { k, v -> message.addAttribute(k, v) }
    val text = message.toString() + "\n"

    if (flushEachChar > 0) {
      text.chunked(flushEachChar).forEach { c -> converter.process(c, ProcessOutputTypes.STDOUT) }
    }
    else {
      converter.process(text, ProcessOutputTypes.STDOUT)
    }
  }
}