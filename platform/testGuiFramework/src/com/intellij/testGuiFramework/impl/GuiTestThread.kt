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

import com.intellij.testGuiFramework.remote.JUnitClient
import com.intellij.testGuiFramework.remote.JUnitClientListener
import com.intellij.testGuiFramework.remote.ObjectSender
import org.junit.runner.JUnitCore
import org.junit.runner.Request

/**
 * @author Sergey Karashevich
 */
class GuiTestThread() : Thread(GUI_TEST_THREAD_NAME) {

  private var myClient: JUnitClient? = null
  private val core = JUnitCore()

  companion object {
    val GUI_TEST_THREAD_NAME = "GuiTest thread"
  }

  override fun run() {
    createAndStartClient()
    addListenerToCore()

    try {
      while (true) {
        val jUnitTestContainer = myClient!!.testQueue.take()
        runTest(jUnitTestContainer.testClass, jUnitTestContainer.methodName)
      }
    }
    catch (e: InterruptedException) {
      Thread.currentThread().interrupt()
    }
  }

  private fun addListenerToCore() {
    assert(myClient!!.objectOutputStream != null)
    val objectSender = ObjectSender(myClient!!.objectOutputStream!!)
    val myListener = JUnitClientListener(objectSender)
    core.addListener(myListener)
  }

  private fun createAndStartClient() {
    assert(myClient == null)
    myClient = JUnitClient(host(), port())
    myClient!!.startClientIfNecessary()
  }

  private fun host(): String = System.getProperty(GuiTestStarter.GUI_TEST_HOST)

  private fun port(): Int = System.getProperty(GuiTestStarter.GUI_TEST_PORT).toInt()

  private fun runTest(testClass: Class<*>, methodName: String) {
    //todo: assert that testClass is derived from GuiTestCase
    val request = Request.method(testClass, methodName)
    core.run(request)
  }


}