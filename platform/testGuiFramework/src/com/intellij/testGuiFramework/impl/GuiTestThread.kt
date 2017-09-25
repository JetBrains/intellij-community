/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.testGuiFramework.impl

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.impl.ApplicationImpl
import com.intellij.openapi.diagnostic.Logger
import com.intellij.testGuiFramework.launcher.GuiTestOptions
import com.intellij.testGuiFramework.remote.JUnitClientListener
import com.intellij.testGuiFramework.remote.client.ClientHandler
import com.intellij.testGuiFramework.remote.client.JUnitClient
import com.intellij.testGuiFramework.remote.client.JUnitClientImpl
import com.intellij.testGuiFramework.remote.transport.JUnitTestContainer
import com.intellij.testGuiFramework.remote.transport.MessageType
import com.intellij.testGuiFramework.remote.transport.TransportMessage
import org.junit.runner.JUnitCore
import org.junit.runner.Request
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
    val GUI_TEST_THREAD_NAME = "GuiTest Thread"
    var client: JUnitClient? = null
  }

  override fun run() {
    client = JUnitClientImpl(host(), port(), createInitHandlers())
    client!!.addHandler(createCloseHandler())

    val myListener = JUnitClientListener({ jUnitInfo -> client!!.send(TransportMessage(MessageType.JUNIT_INFO, jUnitInfo)) })
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
        if (content.additionalInfo.isEmpty()) throw Exception("Cannot resume test without any additional info (label where to resume) in JUnitTestContainer")
        System.setProperty(GuiTestOptions.RESUME_LABEL, content.additionalInfo)
        System.setProperty(GuiTestOptions.RESUME_TEST, "${content.testClass.canonicalName}#${content.methodName}")
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
    val request = Request.method(testContainer.testClass, testContainer.methodName)
    core.run(request)
  }

}