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
import java.util.concurrent.TimeUnit

/**
 * @author Sergey Karashevich
 */
interface JUnitServer {

  fun send(message: TransportMessage)

  fun receive(): TransportMessage

  fun sendAndWaitAnswer(message: TransportMessage)

  fun sendAndWaitAnswer(message: TransportMessage, timeout: Long, timeUnit: TimeUnit)

  fun addHandler(serverHandler: ServerHandler)

  fun setFailHandler(failHandler: (Throwable) -> Unit)

  fun removeHandler(serverHandler: ServerHandler)

  fun removeAllHandlers()

  fun isConnected(): Boolean

  fun getPort(): Int

  fun stopServer()
}