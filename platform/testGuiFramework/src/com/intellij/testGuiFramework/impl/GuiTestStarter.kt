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

/**
 * @author Sergey Karashevich
 */
class GuiTestStarter : IdeaApplication.IdeStarter(), ApplicationStarter {

  companion object {
    val COMMAND_NAME: String = "guitest"

    val GUI_TEST_PORT: String = "idea.gui.test.port"
    val GUI_TEST_HOST: String = "idea.gui.test.host"
    val GUI_TEST_LIST: String = "idea.gui.test.list"

    fun isGuiTestThread(): Boolean = Thread.currentThread().name == GuiTestThread.GUI_TEST_THREAD_NAME
  }

  private val LOG = Logger.getInstance(this.javaClass)
  private val PORT_UNDEFINED = "undefined"
  private val HOST_LOCALHOST = "localhost"

  private val guiTestThread = GuiTestThread()

  override fun getCommandName(): String = COMMAND_NAME

  override fun premain(args: Array<String>) {
    processArgs(args)
    LOG.info("Starting GuiTest activity")
    guiTestThread.start()
    super.premain(args)
  }

  override fun main(args: Array<String>) {
    val myArgs = removeGuiTestArgs(args)
    super.main(myArgs)
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
