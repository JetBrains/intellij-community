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
import com.intellij.testGuiFramework.launcher.GuiTestLocalLauncher.runIdeLocally
import com.intellij.testGuiFramework.launcher.ide.CommunityIde
import com.intellij.testGuiFramework.launcher.ide.Ide
import com.intellij.testGuiFramework.launcher.ide.IdeType
import com.intellij.testGuiFramework.remote.server.JUnitServerHolder
import com.intellij.testGuiFramework.remote.transport.*
import org.junit.Assert
import org.junit.AssumptionViolatedException
import org.junit.internal.runners.model.EachTestNotifier
import org.junit.runner.Description
import org.junit.runner.notification.Failure
import org.junit.runner.notification.RunListener
import org.junit.runner.notification.RunNotifier
import org.junit.runners.BlockJUnit4ClassRunner
import org.junit.runners.model.FrameworkMethod
import org.junit.runners.model.InitializationError
import kotlin.reflect.KClass


class GuiTestLocalRunner @Throws(InitializationError::class)
  constructor(testClass: Class<*>, val ide: Ide?) : BlockJUnit4ClassRunner(testClass) {

  constructor(testClass: Class<*>): this(testClass, null)

  val SERVER_LOG = org.apache.log4j.Logger.getLogger("#com.intellij.testGuiFramework.framework.GuiTestLocalRunner")!!
  val criticalError = Ref<Boolean>(false)

  override fun runChild(method: FrameworkMethod, notifier: RunNotifier) {

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

    val description = this@GuiTestLocalRunner.describeChild(method)
    val eachNotifier = EachTestNotifier(notifier, description)
    if (criticalError.get()) { eachNotifier.fireTestIgnored(); return }

    SERVER_LOG.info("Starting test on server side: ${testClass.name}#${method.name}")
    val server = JUnitServerHolder.getServer()

    try {
      if (!server.isConnected()) {
        val localIde = ide ?: getIdeFromAnnotation(this@GuiTestLocalRunner.testClass.javaClass)
        runIdeLocally(port = server.getPort(), ide = localIde)
      }
      val jUnitTestContainer = JUnitTestContainer(method.declaringClass, method.name)
      server.send(TransportMessage(MessageType.RUN_TEST, jUnitTestContainer))
    }
    catch (e: Exception) {
      SERVER_LOG.error(e)
      notifier.fireTestIgnored(description)
      Assert.fail(e.message)
    }
    var testIsRunning = true
    while(testIsRunning) {
      val message = server.receive()
      if (message.content is JUnitInfo && message.content.testClassAndMethodName == JUnitInfo.getClassAndMethodName(description)) {
        when (message.content.type) {
          Type.STARTED -> eachNotifier.fireTestStarted()
          Type.ASSUMPTION_FAILURE -> eachNotifier.addFailedAssumption((message.content.obj as Failure).exception as AssumptionViolatedException)
          Type.IGNORED -> { eachNotifier.fireTestIgnored(); testIsRunning = false }
          Type.FAILURE -> eachNotifier.addFailure(message.content.obj as Throwable)
          Type.FINISHED -> { eachNotifier.fireTestFinished(); testIsRunning = false }
          else -> throw UnsupportedOperationException("Unable to recognize received from JUnitClient")
        }
      }
    }
  }

  private fun runOnClientSide(method: FrameworkMethod, notifier: RunNotifier) {

    val runListener: RunListener = object : RunListener() {
      override fun testFailure(failure: Failure?) {
        LOG.error("Test failed: '${testClass.name}.${method.name}'")
        notifier.removeListener(this)
        super.testFailure(failure)
      }

      override fun testFinished(description: Description?) {
        LOG.info("Test finished: '${testClass.name}.${method.name}'")
        notifier.removeListener(this)
        super.testFinished(description)
      }

      override fun testIgnored(description: Description?) {
        LOG.info("Test ignored: '${testClass.name}.${method.name}'")
        notifier.removeListener(this)
        super.testIgnored(description)
      }
    }

    try {

      notifier.addListener(runListener)

      LOG.info("Starting test: '${testClass.name}.${method.name}'")
      if (GuiTestUtil.doesIdeHaveFatalErrors()) {
        notifier.fireTestIgnored(describeChild(method))
        LOG.error("Skipping test '${method.name}': a fatal error has occurred in the IDE")
        notifier.pleaseStop()
      }
      else {
        if (!GuiTestStarter.isGuiTestThread())
          runIdeLocally()
        else
          super.runChild(method, notifier)
      }
    } catch (e: Exception) {
      LOG.error(e)
      throw e
    }
  }

  companion object {
    private val LOG = Logger.getInstance("#com.intellij.testGuiFramework.framework.GuiTestRunner")

    fun getIdeFromAnnotation(clazz: Class<*>): Ide {
      val annotation = clazz.getAnnotation(RunWithIde::class.java)
      val value = annotation?.value
      val ideType = if(value != null) (value as KClass<out IdeType>).java.newInstance() else CommunityIde()
      return Ide(ideType, 0, 0)
    }
  }
}

