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
package com.intellij.testGuiFramework.remote

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.testGuiFramework.remote.transport.JUnitTestContainer
import java.io.EOFException
import java.io.InputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.net.ConnectException
import java.net.Socket
import java.util.concurrent.BlockingQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingQueue

/**
 * @author Sergey Karashevich
 */


class JUnitClientToBeDeleted(val host: String = "localhost", val port: Int) {

  val LOG = Logger.getInstance("#com.intellij.testGuiFramework.remote.JUnitClient")

  var objectOutputStream: ObjectOutputStream? = null
  val clientInit = CountDownLatch(1)
  var myClient: Socket? = null

  var isAlive: Boolean = false
  val testQueue: BlockingQueue<JUnitTestContainer> = LinkedBlockingQueue<JUnitTestContainer>()

  private fun runClientInWaitMode(serverName: String, port: Int) {
    LOG.info("Connecting to $serverName on port $port")
    try {
      myClient = Socket(serverName, port)
    }
    catch (e: ConnectException) {
      LOG.error("Connection to JUnit Server is refused", e)
      ApplicationManager.getApplication().invokeAndWait(Runnable {
        ApplicationManager.getApplication().exit()
      })
      return
    }

    LOG.info("Just connected to " + myClient!!.remoteSocketAddress)
    val outToServer = myClient!!.getOutputStream()
    val inFromServer = myClient!!.getInputStream()

    try {
      objectOutputStream = ObjectOutputStream(outToServer)
      clientInit.countDown()
      processIncomingObjects(inFromServer) { handleObject(it) }

    }
    finally {
      stopClient()
      outToServer.close()
      inFromServer.close()
    }
  }

  private fun processIncomingObjects(inFromServer: InputStream?, objectHandler: (Any) -> Unit) {
    val inputStream = ObjectInputStream(inFromServer)
    try {
      while (myClient != null && myClient!!.isConnected)
        objectHandler(inputStream.readObject())
    }
    catch (eof: EOFException) {
      LOG.warn("Client message transport exception: ${eof.message}")
    }
    finally {
      inputStream.close()
    }
  }

  private fun handleObject(obj: Any) {
    LOG.info("Trying to process ${obj.toString()} received from server")
    when (obj) {
      is JUnitTestContainer -> {
        //todo: check that class is derived from GuiTestCase
        testQueue.put(obj)
      }
//      is IdeControlCommand -> IdeCommandProcessor.process(obj)
      else -> LOG.error("Unsupported type to handle it: ${obj.toString()}")
    }
  }

  fun startClientIfNecessary() {
    if (!isAlive) {
      LOG.info("Client hasn't been started yet or is not alive => starting...")
      object : Thread("IDE JUnit client thread") {
        override fun run() = runClientInWaitMode(host, port)
      }.start()
      clientInit.await()
      isAlive = true
    }
  }

  fun stopClient() {
    myClient!!.close()
    myClient = null
    isAlive = false
    LOG.info("Client disconnected")
  }

}

object IdeCommandProcessor {

//  fun process(command: IdeControlCommand) {
//    when(command.command) {
//      CLOSE_IDE -> { ApplicationManager.getApplication().exit() }
//    }
//  }
}