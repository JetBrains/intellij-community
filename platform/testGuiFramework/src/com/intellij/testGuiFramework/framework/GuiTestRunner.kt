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
package com.intellij.testGuiFramework.framework

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.Ref
import com.intellij.testGuiFramework.impl.GuiTestStarter
import com.intellij.testGuiFramework.impl.GuiTestThread
import com.intellij.testGuiFramework.impl.GuiTestUtilKt
import com.intellij.testGuiFramework.launcher.GuiTestLocalLauncher
import com.intellij.testGuiFramework.launcher.GuiTestLocalLauncher.runIdeLocally
import com.intellij.testGuiFramework.launcher.ide.Ide
import com.intellij.testGuiFramework.remote.server.JUnitServer
import com.intellij.testGuiFramework.remote.server.JUnitServerHolder
import com.intellij.testGuiFramework.remote.transport.*
import org.junit.Assert
import org.junit.AssumptionViolatedException
import org.junit.internal.runners.model.EachTestNotifier
import org.junit.runner.Description
import org.junit.runner.notification.Failure
import org.junit.runner.notification.RunListener
import org.junit.runner.notification.RunNotifier
import org.junit.runners.model.FrameworkMethod
import java.util.concurrent.TimeUnit


class GuiTestRunner internal constructor(val runner: GuiTestRunnerInterface) {

  private val SERVER_LOG = org.apache.log4j.Logger.getLogger("#com.intellij.testGuiFramework.framework.GuiTestRunner")!!
  private val criticalError = Ref<Boolean>(false)


  fun runChild(method: FrameworkMethod, notifier: RunNotifier) {
    if (!GuiTestStarter.isGuiTestThread())
      runOnServerSide(method, notifier)
    else
      runOnClientSide(method, notifier)
  }

  /**
   * it suites only to test one test class. IntelliJ IDEA starting with "guitest" argument and list of tests. So we cannot calculate a list
   * of tests on invoking of this method. Therefore could be launched one test only.
   *
   * We are not relaunching IDE if it has been already started. We assume that test argument passed and it is only one.
   */
  private fun runOnServerSide(method: FrameworkMethod, notifier: RunNotifier) {

    val description = runner.describeChild(method)

    val eachNotifier = EachTestNotifier(notifier, description)
    if (criticalError.get()) {
      eachNotifier.fireTestIgnored(); return
    }

    val testName = runner.getTestName(method.name)
    SERVER_LOG.info("Starting test on server side: $testName")
    val server = JUnitServerHolder.getServer()

    try {
      if (!server.isConnected()) {
        val localIde = runner.ide ?: getIdeFromAnnotation(method.declaringClass)
        runIde(port = server.getPort(), ide = localIde)
        if (!server.isStarted()) {
          server.start()
        }
      }
      val jUnitTestContainer = JUnitTestContainer(method.declaringClass, testName)
      server.send(TransportMessage(MessageType.RUN_TEST, jUnitTestContainer))
    }
    catch (e: Exception) {
      SERVER_LOG.error(e)
      notifier.fireTestIgnored(description)
      Assert.fail(e.message)
    }
    var testIsRunning = true
    while (testIsRunning) {
      val message = server.receive()
      if (message.content is JUnitInfo && message.content.testClassAndMethodName == JUnitInfo.getClassAndMethodName(description)) {
        when (message.content.type) {
          Type.STARTED -> eachNotifier.fireTestStarted()
          Type.ASSUMPTION_FAILURE -> eachNotifier.addFailedAssumption(
            (message.content.obj as Failure).exception as AssumptionViolatedException)
          Type.IGNORED -> {
            eachNotifier.fireTestIgnored(); testIsRunning = false
          }
          Type.FAILURE -> eachNotifier.addFailure(message.content.obj as Throwable)
          Type.FINISHED -> {
            eachNotifier.fireTestFinished(); testIsRunning = false
          }
          else -> throw UnsupportedOperationException("Unable to recognize received from JUnitClient")
        }
      }
      if (message.type == MessageType.RESTART_IDE) {
        restartIdeAndStartTestAgain(server, method)
        sendRunTestCommand(method, server)
      }
      if (message.type == MessageType.RESTART_IDE_AND_RESUME) {
        val additionalInfoLabel = message.content
        if (additionalInfoLabel !is String) throw Exception("Additional info for a resuming test should have a String type!")
        restartIdeAndStartTestAgain(server, method)
        sendResumeTestCommand(method, server, additionalInfoLabel)
      }
    }
  }

  private fun restartIdeAndStartTestAgain(server: JUnitServer, method: FrameworkMethod) {
    //close previous IDE
    server.send(TransportMessage(MessageType.CLOSE_IDE))
    //await to close previous process
    GuiTestLocalLauncher.process?.waitFor(2, TimeUnit.MINUTES)
    //restart JUnitServer to let accept a new connection
    server.stopServer()
    //start a new one IDE
    val localIde = runner.ide ?: getIdeFromAnnotation(method.declaringClass)
    runIde(port = server.getPort(), ide = localIde)
    server.start()
  }

  private fun sendRunTestCommand(method: FrameworkMethod,
                                 server: JUnitServer) {
    val jUnitTestContainer = JUnitTestContainer(method.declaringClass, method.name)
    server.send(TransportMessage(MessageType.RUN_TEST, jUnitTestContainer))
  }

  private fun sendResumeTestCommand(method: FrameworkMethod,
                                    server: JUnitServer, resumeTestLabel: String) {
    val jUnitTestContainer = JUnitTestContainer(method.declaringClass, method.name, additionalInfo = resumeTestLabel)
    server.send(TransportMessage(MessageType.RESUME_TEST, jUnitTestContainer))
  }

  private fun runOnClientSide(method: FrameworkMethod, notifier: RunNotifier) {
    val testName = runner.getTestName(method.name)

    val runListener: RunListener = object : RunListener() {
      override fun testFailure(failure: Failure?) {
        LOG.error("Test failed: '$testName'")
        notifier.removeListener(this)
        super.testFailure(failure)
      }

      override fun testFinished(description: Description?) {
        LOG.info("Test finished: '$testName'")
        notifier.removeListener(this)
        super.testFinished(description)
      }

      override fun testIgnored(description: Description?) {
        LOG.info("Test ignored: '$testName'")
        notifier.removeListener(this)
        super.testIgnored(description)
      }
    }

    try {
      notifier.addListener(runListener)
      LOG.info("Starting test: '$testName'")
      //if IDE has a fatal errors from a previous test
      if (GuiTestUtilKt.fatalErrorsFromIde().isNotEmpty() or GuiTestUtil.doesIdeHaveFatalErrors()) {
        val restartIdeMessage = TransportMessage(MessageType.RESTART_IDE,
                                                 "IDE has fatal errors from previous test, let's start a new instance")
        GuiTestThread.client?.send(restartIdeMessage) ?: throw Exception("JUnitClient is accidentally null")
      }
      else {
        if (!GuiTestStarter.isGuiTestThread())
          runIdeLocally() //TODO: investigate this case
        else {
          runner.doRunChild(method, notifier)
        }
      }
    }
    catch (e: Exception) {
      LOG.error(e)
      throw e
    }
  }

  private fun runIde(port: Int, ide: Ide) {
    val testClassNames = runner.getTestClassesNames()
    if (testClassNames.isEmpty()) throw Exception("Test classes are not declared.")
    runIdeLocally(port = port,
                  ide = ide,
                  testClassNames = testClassNames)
  }

  companion object {
    private val LOG = Logger.getInstance("#com.intellij.testGuiFramework.framework.GuiTestRunner")
  }

}
