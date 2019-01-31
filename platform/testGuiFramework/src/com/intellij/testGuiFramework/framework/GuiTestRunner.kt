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

import com.intellij.openapi.application.PathManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.Ref
import com.intellij.testGuiFramework.framework.param.GuiTestLocalRunnerParam
import com.intellij.testGuiFramework.framework.param.GuiTestLocalRunnerParam.Companion.PARAMETERS
import com.intellij.testGuiFramework.impl.GuiTestStarter
import com.intellij.testGuiFramework.impl.GuiTestThread
import com.intellij.testGuiFramework.impl.GuiTestUtilKt
import com.intellij.testGuiFramework.launcher.GradleLauncher
import com.intellij.testGuiFramework.launcher.GuiTestLocalLauncher
import com.intellij.testGuiFramework.launcher.GuiTestOptions
import com.intellij.testGuiFramework.launcher.ide.Ide
import com.intellij.testGuiFramework.remote.IdeControl.closeIde
import com.intellij.testGuiFramework.remote.IdeControl.ensureIdeIsRunning
import com.intellij.testGuiFramework.remote.IdeControl.restartIde
import com.intellij.testGuiFramework.remote.IdeControl.resumeTest
import com.intellij.testGuiFramework.remote.IdeControl.runTest
import com.intellij.testGuiFramework.remote.server.JUnitServer
import com.intellij.testGuiFramework.remote.server.JUnitServerHolder
import com.intellij.testGuiFramework.remote.transport.*
import com.intellij.testGuiFramework.testCases.PluginTestCase.Companion.PLUGINS_INSTALLED
import com.intellij.testGuiFramework.testCases.SystemPropertiesTestCase.Companion.SYSTEM_PROPERTIES
import com.intellij.util.io.exists
import org.junit.Assert
import org.junit.AssumptionViolatedException
import org.junit.internal.runners.model.EachTestNotifier
import org.junit.runner.Description
import org.junit.runner.notification.Failure
import org.junit.runner.notification.RunListener
import org.junit.runner.notification.RunNotifier
import org.junit.runners.model.FrameworkMethod
import java.net.SocketException
import java.nio.file.Files
import java.nio.file.Paths


open class GuiTestRunner internal constructor(open val runner: GuiTestRunnerInterface) {

  private val SERVER_LOG = org.apache.log4j.Logger.getLogger("com.intellij.testGuiFramework.framework.GuiTestRunner")!!
  private val criticalError = Ref(false)

  private val myServer: JUnitServer
    get() = JUnitServerHolder.getServer()


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
    val localIde = runner.ide ?: getIdeFromAnnotation(method.declaringClass)
    val systemProperties = getSystemPropertiesFromAnnotation(method.declaringClass)

    val eachNotifier = EachTestNotifier(notifier, description)
    if (criticalError.get()) {
      eachNotifier.fireTestIgnored(); return
    }

    val testName = runner.getTestName(method.name)
    SERVER_LOG.info("Starting test on server side: $testName")

    try {
      ensureIdeIsRunning(localIde, systemProperties ?: emptyList(), ::runIde)
      sendRunTestCommand(method, testName)
    }
    catch (e: Exception) {
      SERVER_LOG.error(e)
      notifier.fireTestIgnored(description)
      Assert.fail(e.message)
    }
    var testIsRunning = true
    var restartIdeAfterTest = false
    while (testIsRunning) {
      try {
        val message = myServer.receive()
        if (message.content is JUnitInfo && message.content.testClassAndMethodName == JUnitInfo.getClassAndMethodName(description)) {
          if (restartIdeAfterTest && message.content.type == Type.FINISHED) {
            restartIde(ide = getIdeFromMethod(method), runIde = ::runIde)
            //we're removing config/options/recentProjects.xml to avoid auto-opening of the previous project
            deleteRecentProjectsSettings()
            SERVER_LOG.info("Restarting IDE...")
          }
          testIsRunning = processJUnitEvent(message.content, eachNotifier)
        }
        if (message.type == MessageType.RESTART_IDE) {
          restartIde(ide = getIdeFromMethod(method), runIde = ::runIde)
          //we're removing config/options/recentProjects.xml to avoid auto-opening of the previous project
          deleteRecentProjectsSettings()
          sendRunTestCommand(method, testName)
        }
        if (message.type == MessageType.RESTART_IDE_AFTER_TEST) {
          SERVER_LOG.warn("IDE should be restarted after test")
          restartIdeAfterTest = true
        }
        if (message.type == MessageType.RESTART_IDE_AND_RESUME) {
          if (message.content !is RestartIdeAndResumeContainer) throw Exception(
            "Transport exception: Message with type RESTART_IDE_AND_RESUME should have content type RestartIdeAndResumeContainer but has a ${message.content?.javaClass?.canonicalName}")
          when (message.content.restartIdeCause) {
            RestartIdeCause.PLUGIN_INSTALLED -> {
              //do not restart IDE from previously opened project
              deleteRecentProjectsSettings()
              restartIde(ide = getIdeFromMethod(method), runIde = ::runIde)
              resumeTest(method, PLUGINS_INSTALLED)
            }
            RestartIdeCause.RUN_WITH_SYSTEM_PROPERTIES -> {
              if (message.content !is RunWithSystemPropertiesContainer) throw Exception(
                "Transport exception: message.content caused by RUN_WITH_SYSTEM_PROPERTIES should have RunWithSystemPropertiesContainer type, but have: ${message.content.javaClass.canonicalName}")
              restartIde(getIdeFromMethod(method), additionalJvmOptions = message.content.systemProperties, runIde = ::runIde)
              resumeTest(method, SYSTEM_PROPERTIES)
            }
          }
        }
      }
      catch (se: SocketException) {
        //let's fail this test and move to the next one test
        SERVER_LOG.warn("Server client connection is dead. Going to kill IDE processStdIn.")
        closeIde()
        eachNotifier.addFailure(se)
        eachNotifier.fireTestFinished()
        testIsRunning = false
      }
    }
  }

  private fun deleteRecentProjectsSettings() {
    val recentProjects = Paths.get(PathManager.getConfigPath(), "options", "recentProjects.xml")
    if (recentProjects.exists())
      Files.delete(recentProjects)
    val recentProjectDirectories = Paths.get(PathManager.getConfigPath(), "options", "recentProjectDirectories.xml")
    if (recentProjectDirectories.exists())
      Files.delete(recentProjectDirectories)
  }

  private fun sendRunTestCommand(method: FrameworkMethod, testName: String) {
    val jUnitTestContainer = if (runner is GuiTestLocalRunnerParam)
      JUnitTestContainer(method.declaringClass, testName, mapOf(Pair(PARAMETERS, (runner as GuiTestLocalRunnerParam).getParameters())))
    else
      JUnitTestContainer(method.declaringClass, testName)
    runTest(jUnitTestContainer)
  }

  protected fun processJUnitEvent(content: JUnitInfo,
                                  eachNotifier: EachTestNotifier): Boolean {
    return when (content.type) {
      Type.STARTED -> {
        eachNotifier.fireTestStarted(); true
      }
      Type.ASSUMPTION_FAILURE -> {
        eachNotifier.addFailedAssumption((content.obj as Failure).exception as AssumptionViolatedException)
        false
      }
      Type.IGNORED -> {
        eachNotifier.fireTestIgnored()
        false
      }
      Type.FAILURE -> {
        //reconstruct Throwable
        val (className, messageFromException, stackTraceFromException) = content.obj as FailureException
        val throwable = Throwable("thrown from $className: $messageFromException")
        throwable.stackTrace = stackTraceFromException
        eachNotifier.addFailure(throwable)
        true
      }
      Type.FINISHED -> {
        processTestFinished(eachNotifier)
        false
      }
      else -> throw UnsupportedOperationException("Unable to recognize received from JUnitClient")
    }
  }

  protected open fun runIde(ide: Ide, additionalJvmOptions: List<Pair<String, String>> = emptyList()) {
    if (GuiTestOptions.isGradleRunner) {
      GradleLauncher.runIde(JUnitServerHolder.getServer().getPort())
    }
    else {
      val testClassNames = runner.getTestClassesNames()
      if (testClassNames.isEmpty()) throw Exception("Test classes are not declared.")
      GuiTestLocalLauncher.runIdeLocally(ide, JUnitServerHolder.getServer().getPort())
    }
  }

  protected open fun processTestFinished(eachNotifier: EachTestNotifier) {
    eachNotifier.fireTestFinished()
  }

  protected fun getIdeFromMethod(method: FrameworkMethod): Ide {
    return runner.ide ?: getIdeFromAnnotation(method.declaringClass)
  }

  /**
   * @additionalJvmOptions - an array of key-value pairs written without -D, for example: {@code arrayOf(Pair("idea.debug.mode", "true"))
   * By default set as an empty array – no additional JVM options
   */

  private fun runOnClientSide(method: FrameworkMethod, notifier: RunNotifier) {
    val testName = runner.getTestName(method.name)

    val runListener: RunListener = object : RunListener() {
      override fun testFailure(failure: Failure?) {
        LOG.info("Test failed: '$testName'")
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
          TODO("Investigate this case")
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

  companion object {
    private val LOG = Logger.getInstance("#com.intellij.testGuiFramework.framework.GuiTestRunner")
  }

}
