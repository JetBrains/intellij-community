// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testGuiFramework.impl

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.impl.ApplicationImpl
import com.intellij.openapi.diagnostic.Logger
import com.intellij.testGuiFramework.framework.param.GuiTestLocalRunnerParam
import com.intellij.testGuiFramework.launcher.GuiTestOptions
import com.intellij.testGuiFramework.launcher.GuiTestOptions.RESUME_LABEL
import com.intellij.testGuiFramework.remote.JUnitClientListener
import com.intellij.testGuiFramework.remote.client.ClientHandler
import com.intellij.testGuiFramework.remote.client.JUnitClient
import com.intellij.testGuiFramework.remote.client.JUnitClientImpl
import com.intellij.testGuiFramework.remote.transport.JUnitTestContainer
import com.intellij.testGuiFramework.remote.transport.MessageType
import com.intellij.testGuiFramework.remote.transport.TransportMessage
import org.junit.runner.JUnitCore
import org.junit.runner.Request
import org.junit.runners.model.TestClass
import org.junit.runners.parameterized.TestWithParameters
import java.util.concurrent.BlockingQueue
import java.util.concurrent.LinkedBlockingQueue

/**
 * @author Sergey Karashevich
 */
class GuiTestThread : Thread(GUI_TEST_THREAD_NAME) {

  private var testQueue: BlockingQueue<JUnitTestContainer> = LinkedBlockingQueue()
  private val core = JUnitCore()
  private val LOG = Logger.getInstance("#com.intellij.testGuiFramework.impl.GuiTestThread")

  companion object {
    const val GUI_TEST_THREAD_NAME: String = "GuiTest Thread"
    var client: JUnitClient? = null
  }

  override fun run() {
    client = JUnitClientImpl(host(), port(), createInitHandlers())
    client!!.addHandler(createCloseHandler())

    val myListener = JUnitClientListener { jUnitInfo -> client!!.send(TransportMessage(MessageType.JUNIT_INFO, jUnitInfo)) }
    core.addListener(myListener)

    try {
      while (true) {
        val testContainer = testQueue.take()
        LOG.info("Running test: $testContainer")
        runTest(testContainer)
      }
    }
    catch (e: InterruptedException) {
      Thread.currentThread().interrupt()
    }
  }

  private fun createInitHandlers(): Array<ClientHandler> {
    val testHandler = object : ClientHandler {
      override fun accept(message: TransportMessage) = message.type == MessageType.RUN_TEST

      override fun handle(message: TransportMessage) {
        val content = (message.content as JUnitTestContainer)
        LOG.info("Added test to testQueue: ${content.toString()}")
        testQueue.add(content)
      }
    }

    val testResumeHandler = object : ClientHandler {
      override fun accept(message: TransportMessage) = message.type == MessageType.RESUME_TEST

      override fun handle(message: TransportMessage) {
        val content = (message.content as JUnitTestContainer)
        if (!content.additionalInfo.containsKey(RESUME_LABEL)) throw Exception(
          "Cannot resume test without any additional info (label where to resume) in JUnitTestContainer")
        System.setProperty(GuiTestOptions.RESUME_LABEL, content.additionalInfo[RESUME_LABEL].toString())
        System.setProperty(GuiTestOptions.RESUME_TEST, "${content.testClass.canonicalName}#${content.testName}")
        LOG.info("Added test to testQueue: $content")
        testQueue.add(content)
      }
    }

    return arrayOf(testHandler, testResumeHandler)
  }


  private fun createCloseHandler(): ClientHandler {
    return object : ClientHandler {
      override fun accept(message: TransportMessage) = message.type == MessageType.CLOSE_IDE

      override fun handle(message: TransportMessage) {
        client?.send(TransportMessage(MessageType.RESPONSE, null, message.id)) ?: throw Exception(
          "Unable to handle transport message: \"$message\", because JUnitClient is accidentally null")
        val application = ApplicationManager.getApplication()
        (application as ApplicationImpl).exit(true, true)
      }
    }
  }

  private fun host(): String = System.getProperty(GuiTestStarter.GUI_TEST_HOST)

  private fun port(): Int = System.getProperty(GuiTestStarter.GUI_TEST_PORT).toInt()

  private fun runTest(testContainer: JUnitTestContainer) {
    if (testContainer.additionalInfo["parameters"] == null) {
      //todo: replace request with a runner
      val request = Request.method(testContainer.testClass, testContainer.testName)
      core.run(request)
    } else {
      val runner = GuiTestLocalRunnerParam(TestWithParameters(testContainer.testName, TestClass(testContainer.testClass), testContainer.additionalInfo["parameters"] as MutableList<*>))
      core.run(runner)
    }
  }

  private fun String.getParameterisedPart(): String {
    return Regex("\\[(.)*]").find(this)?.value ?: ""
  }

}