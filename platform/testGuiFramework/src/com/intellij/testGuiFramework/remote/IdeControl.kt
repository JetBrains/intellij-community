// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testGuiFramework.remote

import com.intellij.testGuiFramework.launcher.GuiTestOptions.RESUME_LABEL
import com.intellij.testGuiFramework.launcher.ide.Ide
import com.intellij.testGuiFramework.remote.server.JUnitServer
import com.intellij.testGuiFramework.remote.server.JUnitServerHolder
import com.intellij.testGuiFramework.remote.transport.JUnitTestContainer
import com.intellij.testGuiFramework.remote.transport.MessageType
import com.intellij.testGuiFramework.remote.transport.TransportMessage
import org.apache.log4j.Logger
import org.junit.runners.model.FrameworkMethod
import java.io.InputStream
import java.util.concurrent.TimeUnit

typealias RunIdeFun = (Ide, List<Pair<String, String>>) -> Unit

object IdeControl {


  private val LOG = Logger.getLogger(this.javaClass)

  @Volatile
  private var currentIdeProcess: Process? = null

  /**
   * returns current Ide processStdIn
   */
  private fun getIdeProcess(): Process {
    return currentIdeProcess ?: throw Exception("Current IDE processStdIn is not initialised or already been killed")
  }

  private val myServer: JUnitServer
    get() {
      return JUnitServerHolder.getServer()
    }

  /**
   * adds a new IDE processStdIn to control
   */
  fun submitIdeProcess(ideProcess: Process) {
    currentIdeProcess = ideProcess
  }

  fun waitForCurrentProcess(): Int = getIdeProcess().waitFor()

  fun waitForCurrentProcess(timeout: Long, timeUnit: TimeUnit): Boolean = getIdeProcess().waitFor(timeout, timeUnit)

  fun exitValue(): Int = getIdeProcess().exitValue()

  fun getErrorStream(): InputStream = getIdeProcess().errorStream

  fun getInputStream(): InputStream = getIdeProcess().inputStream

  fun closeIde() {
    sendCloseIdeSignal()
    IdeControl.killIdeProcess()
    myServer.stopServer()
  }

  fun restartIde(ide: Ide, additionalJvmOptions: List<Pair<String, String>> = emptyList(), runIde: RunIdeFun) {
    IdeControl.closeIde()
    startIdeAndServer(ide, additionalJvmOptions, runIde)
  }

  /**
   * @additionalJvmOptions - an array of key-value pairs written without -D, for example: {@code arrayOf(Pair("idea.debug.mode", "true"))
   * By default set as an empty array – no additional JVM options
   */

  fun runTest( jUnitTestContainer: JUnitTestContainer) {
    myServer.send(TransportMessage(MessageType.RUN_TEST, jUnitTestContainer))
  }

  fun ensureIdeIsRunning(ide: Ide, additionalJvmOptions: List<Pair<String, String>>, runIde: RunIdeFun) {
    if (!myServer.isConnected()) {
      LOG.info("Starting IDE ($ide) with port for running tests: ${myServer.getPort()}")
      startIdeAndServer(ide = ide, additionalJvmOptions = emptyList(), runIde = runIde)
    }
  }

  fun resumeTest(method: FrameworkMethod, resumeTestLabel: String) {
    val jUnitTestContainer = JUnitTestContainer(method.declaringClass.canonicalName, method.name, additionalInfo = mapOf<String, Any>(Pair(RESUME_LABEL, resumeTestLabel)))
    myServer.send(TransportMessage(MessageType.RESUME_TEST, jUnitTestContainer))
  }

  private fun sendCloseIdeSignal() {
    if (!myServer.isStarted()) return
    myServer.send(TransportMessage(MessageType.CLOSE_IDE))
    IdeControl.waitForCurrentProcess(2, TimeUnit.MINUTES)
  }

  private fun startIdeAndServer(ide: Ide, additionalJvmOptions: List<Pair<String, String>> = emptyList(), runIde: RunIdeFun) {
    runIde(ide, additionalJvmOptions)
    myServer.start()
  }

  /**
   * kills IDE java processStdIn if the pipeline for sockets has been crashed or IDE doesn't respond
   */
  private fun killIdeProcess() {
    currentIdeProcess?.destroyForcibly()?.waitFor(2, TimeUnit.MINUTES)
    ?: throw Exception("Current IDE processStdIn is not initialised or already been killed")
  }
}
