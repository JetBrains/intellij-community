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

import com.intellij.testGuiFramework.launcher.GuiTestLocalLauncher
import com.intellij.testGuiFramework.launcher.ide.Ide
import com.intellij.testGuiFramework.remote.server.JUnitServerHolder
import com.intellij.testGuiFramework.remote.server.ServerHandler
import com.intellij.testGuiFramework.remote.transport.*
import java.util.concurrent.TimeUnit

/**
 * @author Sergey Karashevich
 */
abstract class RemoteTestCase {

  fun startIde(ide: Ide,
               host: String = "localhost",
               port: Int,
               path: String = "undefined",
               body: IdeTestFixture.() -> Unit) {
    with(IdeTestFixture(ide, host, port, path)) {
      start()
      body()
    }
  }

  fun installPlugin(ide: com.intellij.testGuiFramework.launcher.ide.Ide) {
    TODO("add body here")
  }

  fun startAndClose(ide: com.intellij.testGuiFramework.launcher.ide.Ide,
                    host: String = "localhost",
                    port: Int,
                    path: String = "undefined",
                    body: com.intellij.testGuiFramework.remote.IdeTestFixture.() -> Unit) {
    with(IdeTestFixture(ide, host, port, path)) {
      start()
      body()
      close()
    }
  }

}

class IdeTestFixture(val ide: Ide,
                     val host: String,
                     val port: Int,
                     val path: String = "undefined") {

  fun start() {
    val server = JUnitServerHolder.getServer() // ensure that server has been started
    val serverPort = server.getPort()
    if (path == "undefined") {
      if (!server.isConnected()) {
        GuiTestLocalLauncher.runIdeLocally(ide, serverPort)
      } else {
        TODO("idea is already started")
      }
    }
    else {
      //ensure that TestGuiFramework has been copied
      //copy test classes or add additional classpath as an argument
      GuiTestLocalLauncher.runIdeByPath(path, ide, serverPort)
    }
  }

  fun close() {
    JUnitServerHolder.getServer().sendAndWaitAnswer(TransportMessage(MessageType.CLOSE_IDE), 300L, TimeUnit.SECONDS)
  }

  fun runTest(testClassAndMethod: String,
              timeout: Long = 0,
              timeUnit: java.util.concurrent.TimeUnit = java.util.concurrent.TimeUnit.SECONDS) {

    val (testClassName, testMethodName) = testClassAndMethod.split("#")
    val declaringClass = Class.forName(testClassName)

    val myCountDownLatch = java.util.concurrent.CountDownLatch(1)
    val myServerHandler = createServerHandlerForTest(testClassAndMethod, myCountDownLatch)

    with(JUnitServerHolder.getServer()) {
      addHandler(myServerHandler)
      setFailHandler({ throwable -> myCountDownLatch.countDown(); throw throwable })
      send(runTestMessage(declaringClass, testMethodName)) // send container with test to IDE with started JUnitClient to run test
      if (timeout == 0L)
        myCountDownLatch.await()
      else
        myCountDownLatch.await(timeout, timeUnit)
      removeHandler(myServerHandler)
    }

  }

  private fun runTestMessage(declaringClass: Class<*>, methodName: String): TransportMessage
    = TransportMessage(MessageType.RUN_TEST, JUnitTestContainer(declaringClass, methodName))

  private fun createServerHandlerForTest(testName: String, conditionToFinish: java.util.concurrent.CountDownLatch): ServerHandler {

    return object : ServerHandler() {

      override fun acceptObject(message: TransportMessage) = message.content is JUnitInfo

      override fun handleObject(message: TransportMessage) {
        val jUnitInfo = message.content as JUnitInfo
        when (jUnitInfo.type) {
          Type.STARTED -> println("Test '$testName' started")
          Type.ASSUMPTION_FAILURE -> {
            println("Test '$testName' assumption error"); conditionToFinish.countDown()
          }
          Type.IGNORED -> {
            println("Test '$testName' ignored"); conditionToFinish.countDown()
          }
          Type.FAILURE -> {
            println("Test '$testName' failed");
//            (jUnitInfo.obj as Array<StackTraceElement>)
//              .forEach { System.err.println(it) }
            val t = jUnitInfo.obj as Throwable
            System.err.println(t)
            t.printStackTrace(System.err)
            conditionToFinish.countDown()
          }
          Type.FINISHED -> {
            println("Test '$testName' finished"); conditionToFinish.countDown()
          }
          else -> throw UnsupportedOperationException("Unable to recognize received from JUnitClient")
        }
      }
    }
  }

}