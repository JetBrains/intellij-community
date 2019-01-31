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

import com.intellij.testGuiFramework.remote.transport.MessageType
import com.intellij.testGuiFramework.remote.transport.TransportMessage
import org.apache.log4j.Logger
import java.io.InvalidClassException
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.util.*
import java.util.concurrent.BlockingQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/**
 * @author Sergey Karashevich
 */

class JUnitServerImpl : JUnitServer {

  private val SEND_THREAD = "JUnit Server Send Thread"
  private val RECEIVE_THREAD = "JUnit Server Receive Thread"
  private val postingMessages: BlockingQueue<TransportMessage> = LinkedBlockingQueue()
  private val receivingMessages: BlockingQueue<TransportMessage> = LinkedBlockingQueue()
  private val handlers: ArrayList<ServerHandler> = ArrayList()
  private var failHandler: ((Throwable) -> Unit)? = null
  private val LOG = Logger.getLogger("#com.intellij.testGuiFramework.remote.server.JUnitServerImpl")

  private val serverSocket = ServerSocket(0)
  private lateinit var serverSendThread: ServerSendThread
  private lateinit var serverReceiveThread: ServerReceiveThread
  private lateinit var connection: Socket
  private var isStarted = false

  private val IDE_STARTUP_TIMEOUT = 180000

  private val port: Int

  init {
    port = serverSocket.localPort
    serverSocket.soTimeout = IDE_STARTUP_TIMEOUT
  }

  override fun start() {
    connection = serverSocket.accept()
    LOG.info("Server accepted client on port: ${connection.port}")

    serverSendThread = ServerSendThread()
    serverSendThread.start()

    serverReceiveThread = ServerReceiveThread()
    serverReceiveThread.start()
    isStarted = true
  }

  override fun isStarted(): Boolean = isStarted

  override fun send(message: TransportMessage) {
    postingMessages.put(message)
    LOG.info("Add message to send pool: $message ")
  }

  override fun receive(): TransportMessage {
    return receivingMessages.poll(IDE_STARTUP_TIMEOUT.toLong(), TimeUnit.MILLISECONDS)
           ?: throw SocketException("Client doesn't respond. Either the test has hanged or IDE crushed.")
  }

  override fun sendAndWaitAnswer(message: TransportMessage): Unit = sendAndWaitAnswerBase(message)

  override fun sendAndWaitAnswer(message: TransportMessage, timeout: Long, timeUnit: TimeUnit): Unit = sendAndWaitAnswerBase(message, timeout, timeUnit)

  private fun sendAndWaitAnswerBase(message: TransportMessage, timeout: Long = 0L, timeUnit: TimeUnit = TimeUnit.SECONDS) {
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
    return try {
      connection.isConnected && !connection.isClosed
    }
    catch (lateInitException: UninitializedPropertyAccessException) {
      false
    }
  }

  override fun getPort(): Int = port

  override fun stopServer() {
    if (!isStarted) return
    serverSendThread.interrupt()
    LOG.info("Server Send Thread joined")
    serverReceiveThread.interrupt()
    LOG.info("Server Receive Thread joined")
    connection.close()
    isStarted = false
  }

  private fun createCallbackServerHandler(handler: (TransportMessage) -> Unit, id: Long)
    = object : ServerHandler() {
    override fun acceptObject(message: TransportMessage) = message.id == id
    override fun handleObject(message: TransportMessage) {
      handler(message)
    }
  }

  private inner class ServerSendThread: Thread(SEND_THREAD) {

    override fun run() {
      LOG.info("Server Send Thread started")
      ObjectOutputStream(connection.getOutputStream()).use { outputStream ->
        try {
          while (!connection.isClosed) {
            val message = postingMessages.take()
            LOG.info("Sending message: $message ")
            outputStream.writeObject(message)
          }
        }
        catch (e: Exception) {
          when (e) {
            is InterruptedException -> { /* ignore */ }
            is InvalidClassException -> LOG.error("Probably client is down:", e)
            else -> {
              LOG.info(e)
              failHandler?.invoke(e)
            }
          }
        }
      }
    }
  }

  private inner class ServerReceiveThread: Thread(RECEIVE_THREAD) {

    override fun run() {
      LOG.info("Server Receive Thread started")
      ObjectInputStream(connection.getInputStream()).use { inputStream ->
        try {
          while (!connection.isClosed) {
            val obj = inputStream.readObject()
            LOG.debug("Receiving message (DEBUG): $obj")
            assert(obj is TransportMessage)
            val message = obj as TransportMessage
            if (message.type != MessageType.KEEP_ALIVE) LOG.info("Receiving message: $obj")
            receivingMessages.put(message)
            handlers.filter { it.acceptObject(message) }.forEach { it.handleObject(message) }
          }
        }
        catch (e: Exception) {
          when (e) {
            is InterruptedException -> { /* ignore */ }
            is InvalidClassException -> LOG.error("Probably serialization error:", e)
            else -> {
              LOG.info(e)
              failHandler?.invoke(e)
            }
          }
        }
      }
    }
  }
}