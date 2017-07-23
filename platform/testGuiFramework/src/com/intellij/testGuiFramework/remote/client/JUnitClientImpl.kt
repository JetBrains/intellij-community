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
package com.intellij.testGuiFramework.remote.client

import com.intellij.openapi.diagnostic.Logger
import com.intellij.testGuiFramework.remote.transport.TransportMessage
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.net.Socket
import java.util.*
import java.util.concurrent.BlockingQueue
import java.util.concurrent.LinkedBlockingQueue

/**
 * @author Sergey Karashevich
 */
class JUnitClientImpl(val host: String, val port: Int, initHandlers: Array<ClientHandler>? = null) : JUnitClient {

  private val LOG = Logger.getInstance("#com.intellij.testGuiFramework.remote.client.JUnitClientImpl")
  private val RECEIVE_THREAD = "JUnit Client Receive Thread"
  private val SEND_THREAD = "JUnit Client Send Thread"

  private val connection: Socket
  private val clientReceiveThread: ClientReceiveThread
  private val clientSendThread: ClientSendThread
  private val poolOfMessages: BlockingQueue<TransportMessage> = LinkedBlockingQueue()

  private val objectInputStream: ObjectInputStream
  private val objectOutputStream: ObjectOutputStream
  private val handlers: ArrayList<ClientHandler> = ArrayList()


  init {
    if (initHandlers != null) handlers.addAll(initHandlers)

    LOG.info("Client connecting to Server($host, $port) ...")
    connection = Socket(host, port)
    LOG.info("Client connected to Server($host, $port) successfully")

    objectOutputStream = ObjectOutputStream(connection.getOutputStream())
    clientSendThread = ClientSendThread(connection, objectOutputStream)
    clientSendThread.start()

    objectInputStream = ObjectInputStream(connection.getInputStream())
    clientReceiveThread = ClientReceiveThread(connection, objectInputStream)
    clientReceiveThread.start()
  }

  override fun addHandler(handler: ClientHandler) {
    handlers.add(handler)
  }

  override fun removeHandler(handler: ClientHandler) {
    handlers.remove(handler)
  }

  override fun removeAllHandlers() {
    handlers.clear()
  }

  override fun send(message: TransportMessage) {
    poolOfMessages.add(message)
  }

  override fun stopClient() {
    val clientPort = connection.port
    LOG.info("Stopping client on port: $clientPort ...")
    poolOfMessages.clear()
    handlers.clear()
    clientReceiveThread.objectInputStream.close()
    clientReceiveThread.join()
    clientSendThread.objectOutputStream.close()
    clientSendThread.join()
    connection.close()
    LOG.info("Stopped client on port: $clientPort")
  }

  inner class ClientReceiveThread(val connection: Socket, val objectInputStream: ObjectInputStream) : Thread(RECEIVE_THREAD) {
    override fun run() {
      LOG.info("Starting Client Receive Thread")
      try{
        while (connection.isConnected) {
          val obj = objectInputStream.readObject()
          LOG.info("Received message: $obj")
          obj as TransportMessage
          handlers
            .filter { it.accept(obj) }
            .forEach { it.handle(obj) }
        }
      } catch (e: Exception) {
        LOG.info("Transport receiving message exception", e)
      } finally {
        objectInputStream.close()
      }
    }
  }

  inner class ClientSendThread(val connection: Socket, val objectOutputStream: ObjectOutputStream) : Thread(SEND_THREAD) {

    override fun run() {
      try {
        LOG.info("Starting Client Send Thread")
        while (connection.isConnected) {
          val transportMessage = poolOfMessages.take()
          LOG.info("Sending message: $transportMessage")
          objectOutputStream.writeObject(transportMessage)
        }
      }
      catch(e: InterruptedException) {
        Thread.currentThread().interrupt()
      }
      finally {
        objectOutputStream.close()
      }
    }
  }

}
