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
import org.junit.AssumptionViolatedException
import org.junit.runner.Description
import org.junit.runner.JUnitCore
import org.junit.runner.notification.Failure
import org.junit.runner.notification.RunListener
import java.io.DataInputStream
import java.io.ObjectOutputStream
import java.net.ConnectException
import java.net.Socket
import java.util.concurrent.CountDownLatch

/**
 * @author Sergey Karashevich
 */


class JUnitClient(val host: String = "localhost", val port: Int) {

  val LOG: Logger = Logger.getInstance(this.javaClass)

  var objectOutputStream: ObjectOutputStream? = null
  val clientInit = CountDownLatch(1)
  var myClient: Socket? = null

  var isAlive: Boolean = false
  var core: JUnitCore? = null

  private fun runClient(serverName: String, port: Int) {
    LOG.info("Connecting to $serverName on port $port")
    try {
      myClient = Socket(serverName, port)
    } catch (e: ConnectException) {
      LOG.error("Connection to JUnit Server is refused", e)
      ApplicationManager.getApplication().invokeAndWait(Runnable {
        ApplicationManager.getApplication().exit()
      })
      return
    }

    LOG.info("Just connected to " + myClient!!.remoteSocketAddress)
    val outToServer = myClient!!.getOutputStream()
    val inFromServer = myClient!!.getInputStream()

    objectOutputStream = ObjectOutputStream(outToServer)
    clientInit.countDown()

    while (myClient != null && myClient!!.isConnected) {
      val inputStream = DataInputStream(inFromServer)
      LOG.info("Server response: ${inputStream.readUTF()}")
    }
    stopClient()
  }

  private fun startClientIfNecessary() {
    if (!isAlive) {
      LOG.info("Client hasn't been started yet or is not alive => starting...")
      object : Thread("IDE JUnit client thread") {
        override fun run() {
          runClient(host, port)
        }
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

  fun runTests(vararg testClasses: Class<*>) {
    startClientIfNecessary()
    if (core == null) {
      core = JUnitCore()
      assert(objectOutputStream != null)
      val objectSender = ObjectSender(objectOutputStream!!)
      val myListener = JUnitClientListener(objectSender)
      core!!.addListener(myListener)
    }
    core!!.run(*testClasses)
  }

  class ObjectSender(val objectStream: ObjectOutputStream) {

    val LOG: Logger = Logger.getInstance(this.javaClass)

    fun send(obj: Any) {
      LOG.info("Sending to sever: $obj")
      objectStream.writeObject(obj)
    }
  }


  class JUnitClientListener(val objectSender: ObjectSender) : RunListener() {

    override fun testStarted(description: Description?) {
      objectSender.send(JUnitInfo(Type.STARTED, description))
    }

    override fun testAssumptionFailure(failure: Failure?) {
      objectSender.send(JUnitInfo(Type.ASSUMPTION_FAILURE, failure.friendlySerializable()))
    }

    override fun testFailure(failure: Failure?) {
      objectSender.send(JUnitInfo(Type.FAILURE, failure))
    }

    override fun testFinished(description: Description?) {
      objectSender.send(JUnitInfo(Type.FINISHED, description))
    }

    override fun testIgnored(description: Description?) {
      objectSender.send(JUnitInfo(Type.IGNORED, description))
    }

    private fun Failure?.friendlySerializable(): Failure? {
      if (this == null) return null
      val e = this.exception as AssumptionViolatedException
      val newException = AssumptionViolatedException(e.toString(), e.cause)
      return Failure(this.description, newException)
    }
  }

}
