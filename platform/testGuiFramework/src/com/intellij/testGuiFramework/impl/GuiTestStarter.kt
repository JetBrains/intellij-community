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

import com.intellij.idea.IdeaApplication
import com.intellij.openapi.application.ApplicationStarter
import com.intellij.openapi.diagnostic.Logger
import org.junit.runner.Request

/**
 * @author Sergey Karashevich
 */
class GuiTestStarter : IdeaApplication.IdeStarter(), ApplicationStarter {

  private val LOG = Logger.getInstance(this.javaClass)

  companion object {
    val COMMAND_NAME = "guitest"

    val GUI_TEST_PORT = "idea.gui.test.port"
    val GUI_TEST_HOST = "idea.gui.test.host"
    val GUI_TEST_LIST = "idea.gui.test.list"

    fun isGuiTestThread(): Boolean = Thread.currentThread().name == GuiTestThread.GUI_TEST_THREAD_NAME
  }


  val host: String? by lazy {
    System.getProperty(GUI_TEST_HOST)
  }

  val port: Int? by lazy {
    val guiTestPort = System.getProperty(GUI_TEST_PORT)
    if (guiTestPort == null || guiTestPort == PORT_UNDEFINED) null
    else guiTestPort.toInt()
  }

  val guiTestNamesList: List<String> by lazy {
    System.getProperty(GUI_TEST_LIST)?.split(",")!!
  }



  private val guiTestThread = GuiTestThread()

  override fun getCommandName() = COMMAND_NAME

  private val PORT_UNDEFINED = "undefined"

  private val HOST_LOCALHOST = "localhost"

  override fun premain(args: Array<String>) {
    processArgs(args)
    runActivity(args)
    super.premain(args)
  }

  override fun main(args: Array<String>) {
    val myArgs = removeGuiTestArgs(args)
    super.main(myArgs)
  }

//  fun stopGuiTestThread() {
//    LOG.info("Stopping guiTestThread")
//    assert(Thread.currentThread() != guiTestThread)
//    guiTestThread.join()
//    if (myClient != null) myClient!!.stopClient()
//  }

  fun runActivity(args: Array<String>) {
    LOG.info("Starting GuiTest activity")
    guiTestThread.start()
  }


//  fun run() {
//    assert(myClient == null)
//    val isMethodBased: Boolean = guiTestNamesList.any { it.contains("#") }
//    if (port != null) {
//      myClient = JUnitClient(host!!, port!!)
//      if (isMethodBased)
//        myClient!!.runTestMethod(createTestRequest())
//      else
//        myClient!!.runTests(*classesFromTestNames())
//    }
//    else {
//      val core = JUnitCore()
//      if (isMethodBased)
//        core.run(createTestRequest())
//      else
//        core.run(*classesFromTestNames())
//    }
//  }
//
//  //run on GuiTestThread
//  fun run2() {
//    assert(myClient == null)
//    val isMethodBased: Boolean = guiTestNamesList.any { it.contains("#") }
//    assert (port != null)
//    myClient = JUnitClient(host!!, port!!)
//    if (isMethodBased)
//      myClient!!.runTestMethod(createTestRequest())
//    else
//      myClient!!.runTests(*classesFromTestNames())
//
//  }

  private fun classesFromTestNames(): Array<Class<*>> =
    guiTestNamesList.map { Class.forName(it) }.toTypedArray()

  private fun createTestRequest(): Request {
    assert(guiTestNamesList.size == 1) //we can perform junit test request only for one test class and one method in it
    val classAndMethod = guiTestNamesList.first().split("#")
    val request = Request.method(Class.forName(classAndMethod[0]), classAndMethod[1])!!
    return request
  }

  /**
   * We assume next argument string model: main.app guitest testName1,testName2,testName3 host="localhost" port=5009
   */
  private fun processArgs(args: Array<String>) {
    val guiTestList = args[1].removeSurrounding("\"")
    System.setProperty(GUI_TEST_LIST, guiTestList)
    val hostArg: String? = args.find { arg -> arg.toLowerCase().startsWith("host") }?.substringAfter("host=") ?: HOST_LOCALHOST
    System.setProperty(GUI_TEST_HOST, hostArg!!.removeSurrounding("\""))
    val portArg: String? = args.find { arg -> arg.toLowerCase().startsWith("port") }?.substringAfter("port=") ?: PORT_UNDEFINED
    if (portArg != null)
      System.setProperty(GUI_TEST_PORT, portArg.removeSurrounding("\""))
    else
      System.setProperty(GUI_TEST_PORT, PORT_UNDEFINED)

    LOG.info("Set GUI tests list: $guiTestList")
    LOG.info("Set GUI tests host: $hostArg")
    LOG.info("Set GUI tests port: $portArg")
  }

  private fun removeGuiTestArgs(args: Array<String>): Array<out String>? {
    return args.sliceArray(2..args.lastIndex)  //lets remove guitest keyword and list of guitests
      .filterNot { arg -> arg.startsWith("port") || arg.startsWith("host") }//lets remove host and port from args
      .toTypedArray()
  }

}
