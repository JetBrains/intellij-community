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

import com.intellij.testGuiFramework.impl.GuiTestStarter
import java.io.EOFException
import java.io.IOException
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
import java.util.*
import java.util.concurrent.BlockingQueue
import java.util.concurrent.LinkedBlockingQueue

/**
 * @author Sergey Karashevich
 */

class JUnitServerOldStyle : Thread("JUnitServer thread")
//
//  private var serverSocket: ServerSocket = ServerSocket(0)
//
//  init {
//    serverSocket.soTimeout = 180000 //give 3 minutes timeout to startServer JUnit client with IntelliJ IDEA (should startServer right after MainImpl#main())
//  }
//
//  val myServerHandlers = ArrayList<ServerHandler>()
//  val myLifeCycle = LifeCycle()
//  var connection: Socket? = null
//
//  var myServerFailsHandler: ((Throwable) -> Unit)? = null
//
//  override fun run() {
//    try {
//      while (myLifeCycle.isAlive()) {
////        setListeningPort(serverSocket.localPort)
////        LOG.info("Waiting for JUnit client on port $myListeningPort...")
////        println("Waiting for JUnit client on port $myListeningPort...")
//        connection = serverSocket.accept()
//
////        LOG.info("Just connected to ${connection!!.remoteSocketAddress}")
//        println("Just connected to ${connection!!.remoteSocketAddress}")
//        val ois = ObjectInputStream(connection!!.getInputStream())
//        val out = ObjectOutputStream(connection!!.getOutputStream())
//
//        try {
////          senderThread = SenderThread(connection!!, out)
////          senderThread!!.start()
//
//          while (myLifeCycle.isAlive() && connection!!.isConnected) {
//            val receivedObject = ois.readObject()
//            if (myServerHandlers.isNotEmpty()) {
//              myServerHandlers
////                .filter { it.acceptObject(receivedObject) }
////                .forEach {
////                  it.handleObject(receivedObject)
////                  val answer = it.answerToClient(receivedObject)
////                  if (answer != null) out.writeUTF(answer)
//                }
//            }
//            else {
//              LOG.error("Handler list is empty!")
//              throw Exception("No one handler to process received object!")
//            }
//          }
//        }
//        catch (e: EOFException) {
//          LOG.warn("Test state transport problem (EOFException): ${e.message}")
//        }
//        finally {
//          ois.close()
//          out.close()
//        }
//      }
//    }
//    catch (s: SocketTimeoutException) {
//      myLifeCycle.setAliveFlag(false)
//      myServerFailsHandler?.invoke(s)
//      stopServer()
//      LOG.error("Socket timed out!")
//    }
//    catch (e: IOException) {
//      e.printStackTrace()
//      myLifeCycle.setAliveFlag(false)
//      myServerFailsHandler?.invoke(e)
//      stopServer()
//    }
//    catch (e: Exception) {
//      myLifeCycle.setAliveFlag(false)
//      myServerFailsHandler?.invoke(e)
//      stopServer()
//      LOG.error("Exception: ", e)
//    }
//    finally {
//      if (connection != null) connection!!.close()
//    }
//  }
//
//  fun setServerFailsHandler(runnableThrowable: (Throwable) -> Unit) {
//    myServerFailsHandler = runnableThrowable
//  }
//
//  fun registerServerHandler(serverHandler: ServerHandler) {
//    myServerHandlers.add(serverHandler)
//  }
//
//  fun unregisterServerHandler(serverHandler: ServerHandler) {
//    myServerHandlers.remove(serverHandler)
//  }
//
//  fun removeAllServerHandlers() {
//    myServerHandlers.clear()
//  }
//
//  fun sendObject(content: Any) {
//    assert(senderThread != null)
//    poolOfObjects.put(content)
//  }
//
//  fun sendCommand(command: ControlCommand) {
////    createServerHandler()
////    registerServerHandler()
//    val controlCommand = IdeControlCommand(command)
//    val id = controlCommand.commandId
//    sendObject(controlCommand)
//    //waitAnswerwithI
//  }
//
//  companion object {
//
//    val poolOfObjects: BlockingQueue<Any> = LinkedBlockingQueue<Any>()
//
//    private val LOG = com.intellij.openapi.diagnostic.Logger.getInstance("#com.intellij.testGuiFramework.remote.server.JUnitServer")
//    private var senderThread: SenderThread? = null
//
//    var isStarted: Boolean = false
//    var myServerThread: JUnitServer? = null
//    private var myListeningPort: Int = 0
//
//    fun setListeningPort(newPort: Int) {
//      myListeningPort = newPort
//      System.setProperty(GuiTestStarter.GUI_TEST_PORT, newPort.toString())
//    }
//
//    fun getListeningPort(): Int = myListeningPort
//
//    fun startServer() {
//      try {
//        LOG.info("Starting server...")
//        println("Starting server...")
//        myServerThread = JUnitServer()
//        myServerThread!!.start()
//        isStarted = true
//      }
//      catch (e: IOException) {
//        e.printStackTrace()
//      }
//    }
//
//    fun stopServer() {
//      if (isStarted) {
//        isStarted = false
//        setListeningPort(0)
//        myServerThread!!.join()
//      }
//    }
//
//    fun getServer(): JUnitServer {
//      if (!isStarted) startServer()
//      return myServerThread!!
//    }
//  }
//
//
//}
//
//class LifeCycle {
//
//  private var aliveFlag: Boolean = true
//
//  fun isAlive(): Boolean = aliveFlag
//
//  fun setAliveFlag(flag: Boolean) {
//    aliveFlag = flag
//  }
//}
//
//class SenderThread(val connection: Socket, val objectOutputStream: ObjectOutputStream) : Thread("JUnitServer sender thread") {
//
//  override fun run() {
//    try {
//      while (connection.isConnected)
//        objectOutputStream.writeObject(poolOfObjects.take())
//    }
//    catch(e: InterruptedException) {
//      Thread.currentThread().interrupt()
//    }
//    finally {
//      objectOutputStream.close()
//    }
//  }
//}
