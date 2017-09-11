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
package com.intellij.testGuiFramework.remote.server

import com.intellij.testGuiFramework.remote.transport.TransportMessage
import org.apache.log4j.Logger
import java.io.InvalidClassException
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.net.ServerSocket
import java.net.Socket
import java.util.*
import java.util.concurrent.BlockingQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/**
 * @author Sergey Karashevich
 */

class JUnitServerImpl: JUnitServer {

  private val SEND_THREAD = "JUnit Server Send Thread"
  private val RECEIVE_THREAD = "JUnit Server Receive Thread"
  private val postingMessages: BlockingQueue<TransportMessage> = LinkedBlockingQueue()
  private val receivingMessages: BlockingQueue<TransportMessage> = LinkedBlockingQueue()
  private val handlers: ArrayList<ServerHandler> = ArrayList()
  private var failHandler: ((Throwable) -> Unit)? = null
  private val LOG = Logger.getLogger("#com.intellij.testGuiFramework.remote.server.JUnitServerImpl")

  private val serverSocket = ServerSocket(0)
  lateinit private var serverSendThread: ServerSendThread
  lateinit private var serverReceiveThread: ServerReceiveThread
  lateinit private var connection: Socket

  lateinit private var objectInputStream: ObjectInputStream
  lateinit private var objectOutputStream: ObjectOutputStream

  private val port: Int

  init {
    port = serverSocket.localPort
    serverSocket.soTimeout = 180000
  }

  override fun start() {
    execOnParallelThread {
      try {
        connection = serverSocket.accept()
        LOG.info("Server accepted client on port: ${connection.port}")

        objectOutputStream = ObjectOutputStream(connection.getOutputStream())
        serverSendThread = ServerSendThread(connection, objectOutputStream)
        serverSendThread.start()

        objectInputStream = ObjectInputStream(connection.getInputStream())
        serverReceiveThread = ServerReceiveThread(connection, objectInputStream)
        serverReceiveThread.start()
      } catch (e: Exception) {
        failHandler?.invoke(e)
      }
    }
  }

  override fun send(message: TransportMessage) {
    postingMessages.put(message)
    LOG.info("Add message to send pool: $message ")
  }

  override fun receive(): TransportMessage =
    receivingMessages.take()

  override fun sendAndWaitAnswer(message: TransportMessage)
    = sendAndWaitAnswerBase(message)

  override fun sendAndWaitAnswer(message: TransportMessage, timeout: Long, timeUnit: TimeUnit)
    = sendAndWaitAnswerBase(message, timeout, timeUnit)

  fun sendAndWaitAnswerBase(message: TransportMessage, timeout: Long = 0L, timeUnit: TimeUnit = TimeUnit.SECONDS): Unit {
    val countDownLatch = CountDownLatch(1)
    val waitHandler = createCallbackServerHandler({ countDownLatch.countDown() }, message.id)
    addHandler(waitHandler)
    send(message)
    if (timeout == 0L)
      countDownLatch.await()
    else
      countDownLatch.await(timeout, timeUnit)
    removeHandler(waitHandler)
  }

  override fun addHandler(serverHandler: ServerHandler) {
    handlers.add(serverHandler)
  }

  override fun removeHandler(serverHandler: ServerHandler) {
    handlers.remove(serverHandler)
  }

  override fun removeAllHandlers() {
    handlers.clear()
  }

  override fun setFailHandler(failHandler: (Throwable) -> Unit) {
    this.failHandler = failHandler
  }

  override fun isConnected(): Boolean {
    try {
      return connection.isConnected
    } catch (lateInitException: UninitializedPropertyAccessException) {
      return false
    }
  }

  override fun getPort() = port

  override fun stopServer() {
    serverSendThread.objectOutputStream.close()
    LOG.info("Object output stream closed")
    serverSendThread.interrupt()
    LOG.info("Server Send Thread joined")
    serverReceiveThread.objectInputStream.close()
    LOG.info("Object input stream closed")
    serverReceiveThread.interrupt()
    LOG.info("Server Receive Thread joined")
    connection.close()
  }


  private fun execOnParallelThread(body: () -> Unit) {
    (object: Thread("JUnitServer: Exec On Parallel Thread") { override fun run() { body(); Thread.currentThread().join() } }).start()
  }

  private fun createCallbackServerHandler(handler: (TransportMessage) -> Unit, id: Long)
    = object : ServerHandler() {
      override fun acceptObject(message: TransportMessage) = message.id == id
      override fun handleObject(message: TransportMessage) { handler(message) }
    }

  inner class ServerSendThread(val connection: Socket, val objectOutputStream: ObjectOutputStream) : Thread(SEND_THREAD) {

    override fun run() {
      LOG.info("Server Send Thread started")
      try {
        while (connection.isConnected) {
          val message = postingMessages.take()
          LOG.info("Sending message: $message ")
          objectOutputStream.writeObject(message)
        }
      }
      catch(e: InterruptedException) {
        Thread.currentThread().interrupt()
      }
      catch (e: Exception) {
        if (e is InvalidClassException) LOG.error("Probably client is down:", e)
        failHandler?.invoke(e)
      }
      finally {
        objectOutputStream.close()
      }
    }

  }

  inner class ServerReceiveThread(val connection: Socket, val objectInputStream: ObjectInputStream) : Thread(RECEIVE_THREAD) {

    override fun run() {
      try {
        LOG.info("Server Receive Thread started")
        while (connection.isConnected) {
          val obj = objectInputStream.readObject()
          LOG.info("Receiving message: $obj")
          assert(obj is TransportMessage)
          val message = obj as TransportMessage
          receivingMessages.put(message)
          val copied: Array<ServerHandler> = handlers.toTypedArray().copyOf()
          copied
            .filter { it.acceptObject(message) }
            .forEach { it.handleObject(message) }
        }
      } catch (e: Exception) {
        if (e is InvalidClassException) LOG.error("Probably serialization error:", e)
        failHandler?.invoke(e)
      }
    }
  }
}