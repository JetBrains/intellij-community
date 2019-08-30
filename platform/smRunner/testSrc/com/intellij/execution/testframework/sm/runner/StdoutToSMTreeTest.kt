// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.testframework.sm.runner

import com.intellij.execution.process.ProcessOutputTypes
import com.intellij.execution.testframework.sm.ServiceMessageBuilder
import org.junit.Assert


class StdoutToSMTreeTest : BaseSMTRunnerTestCase() {
  private val converter: OutputToGeneralTestEventsConverter by lazy { OutputToGeneralTestEventsConverter("MyTest", false, false, true) }

  private var flushBufferSize = 0


  private val suiteName = "suite_dummy"
  private val testFailName = "test_dummy"
  private val testSuccessName = "test_dummy_2"

  fun testIdBased() {
    buildTestTree(idBased = true, flushBufferSize = 0)
  }

  fun testIdBasedFlushEachChar() {
    buildTestTree(idBased = true, flushBufferSize = 1)
  }

  fun testIdBasedFlushEachFourChars() {
    buildTestTree(idBased = true, flushBufferSize = 4)
  }


  fun testGeneric() {
    buildTestTree(idBased = false, flushBufferSize = 0)
  }

  fun testGenericFlushEachChar() {
    buildTestTree(idBased = false, flushBufferSize = 1)
  }

  fun testGenericFlushEachFourChars() {
    buildTestTree(idBased = false, flushBufferSize = 4)
  }


  private fun buildTestTree(idBased: Boolean, flushBufferSize: Int) {
    this.flushBufferSize = flushBufferSize
    val testProxy = SMTestProxy.SMRootTestProxy()
    converter.processor = if (idBased) {
      GeneralIdBasedToSMTRunnerEventsConvertor(project, testProxy, "root")
    }
    else {
      GeneralToSMTRunnerEventsConvertor(project, testProxy, "root")
    }

    converter.setTestingStartedHandler {  }
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
    message(ServiceMessageBuilder.testFinished(testFailName))

    message(ServiceMessageBuilder.testStarted(testSuccessName))
    message(ServiceMessageBuilder.testFinished(testSuccessName))

    message(ServiceMessageBuilder.testSuiteFinished(suiteName))
  }


  private fun finish() {
    message(ServiceMessageBuilder("testingFinished"))
  }

  private fun start() {
    message(ServiceMessageBuilder("enteredTheMatrix"))
    // OutputToGeneralTestEventsConverter.MyServiceMessageVisitor.visitServiceMessage ignores the first testingStarted event
    message(ServiceMessageBuilder("testingStarted"))
    message(ServiceMessageBuilder("testingStarted"))
  }

  private fun message(message: ServiceMessageBuilder, key: String, value: String) {
    message(message, mapOf(key to value))
  }

  private fun message(message: ServiceMessageBuilder, attrs: Map<String, String> = emptyMap()) {
    attrs.forEach { k, v -> message.addAttribute(k, v) }
    val text = message.toString() + "\n"

    if (flushBufferSize > 0) {
      text.chunked(flushBufferSize).forEach { c -> converter.process(c, ProcessOutputTypes.STDOUT) }
    }
    else {
      converter.process(text, ProcessOutputTypes.STDOUT)
    }
  }
}