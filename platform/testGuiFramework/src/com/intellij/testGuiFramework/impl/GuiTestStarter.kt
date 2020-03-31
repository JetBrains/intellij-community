// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testGuiFramework.impl

import com.intellij.idea.IdeStarter
import com.intellij.openapi.diagnostic.Logger

/**
 * @author Sergey Karashevich
 */
class GuiTestStarter : IdeStarter() {
  companion object {
    const val COMMAND_NAME = "guitest"

    const val GUI_TEST_PORT = "idea.gui.test.port"
    const val GUI_TEST_HOST = "idea.gui.test.host"
    const val GUI_TEST_LIST = "idea.gui.test.list"

    fun isGuiTestThread() = Thread.currentThread().name == GuiTestThread.GUI_TEST_THREAD_NAME
  }

  private val LOG = Logger.getInstance(this.javaClass)
  private val PORT_UNDEFINED = "undefined"
  private val HOST_LOCALHOST = "localhost"

  override fun getCommandName() = COMMAND_NAME

  override fun premain(args: List<String>) {
    val guiTestThread = GuiTestThread()
    processArgs(args)
    LOG.info("Starting GuiTest activity")
    guiTestThread.start()
    super.premain(args)
  }

  override fun main(args: Array<String>) {
    super.main(removeGuiTestArgs(args))
  }

  /**
   * We assume next argument string model: main.app guitest testName1,testName2,testName3 host="localhost" port=5009
   */
  private fun processArgs(args: List<String>) {
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

  private fun removeGuiTestArgs(args: Array<String>): Array<String> {
    return args.sliceArray(2..args.lastIndex)  //lets remove guitest keyword and list of guitests
      .filterNot { arg -> arg.startsWith("port") || arg.startsWith("host") }//lets remove host and port from args
      .toTypedArray()
  }

}
