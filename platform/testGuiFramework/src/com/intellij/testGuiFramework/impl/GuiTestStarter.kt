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
import com.intellij.testGuiFramework.remote.JUnitClient

/**
 * @author Sergey Karashevich
 */
class GuiTestStarter(): IdeaApplication.IdeStarter(), ApplicationStarter {

  private val LOG = Logger.getInstance(this.javaClass)

  val GUI_TEST_PORT = "idea.gui.test.port"
  val GUI_TEST_HOST = "idea.gui.test.host"
  val GUI_TEST_LIST = "idea.gui.test.list"

  val host: String by lazy {
    System.getProperty(GUI_TEST_HOST)
  }

  val port: Int by lazy {
    System.getProperty(GUI_TEST_PORT)!!.toInt()
  }

  val guiTestList: List<Class<*>> by lazy {
    val listOfTestNames = System.getProperty(GUI_TEST_LIST)?.split(",")!!
    listOfTestNames.map { testName -> Class.forName(testName) }
  }

  private var myClient: JUnitClient? = null

  private val guiTestThread: Thread by lazy {
    object : Thread("GuiTest thread") {
      override fun run() {
        this@GuiTestStarter.run()
      }
    }
  }

  override fun getCommandName() = "guitest"

  override fun premain(args: Array<String>) {
    processArgs(args)
    runActivity(args)
    super.premain(args)
  }

  override fun main(args: Array<String>) {
    val myArgs = removeGuiTestArgs(args)
    super.main(myArgs)
  }

  fun stopGuiTestThread() {
    LOG.info("Stopping guiTestThread")
    assert(Thread.currentThread() != guiTestThread)
    guiTestThread.join()
    if (myClient != null) myClient!!.stopClient()
  }

  fun runActivity(args: Array<String>) {
    LOG.info("Starting GuiTest activity")
    guiTestThread.start()
  }

  fun run() {
    assert(myClient == null)
    myClient = JUnitClient(host, port)
    myClient!!.runTests(*guiTestList.toTypedArray())
  }

  /**
   * We assume next argument string model: main.app -guitest testName1,testName2,testName3 host="localhost" port=5555
   */
  private fun processArgs(args: Array<String>) {
    val guiTestList = args[1].removeSurrounding("\"")
    System.setProperty(GUI_TEST_LIST, guiTestList)
    val hostArg = args.find { arg -> arg.toLowerCase().startsWith("host") }?.substringAfter("host=") ?: "localhost"
    System.setProperty(GUI_TEST_HOST, hostArg.removeSurrounding("\""))
    val portArg = args.find { arg -> arg.toLowerCase().startsWith("port") }?.substringAfter("port=") ?: throw Exception("Unable to read port value from args: $args")
    System.setProperty(GUI_TEST_PORT, portArg.removeSurrounding("\""))
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
