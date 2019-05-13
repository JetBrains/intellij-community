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
package org.jetbrains.rpc

const val CONNECTION_CLOSED_MESSAGE: String = "Connection closed"

abstract class MessageManagerBase {
  @Volatile protected var closed: Boolean = false

  protected fun rejectIfClosed(callback: RequestCallback<*>): Boolean {
    if (closed) {
      callback.onError("Connection closed")
      return true
    }
    return false
  }

  fun closed() {
    closed = true
  }
}

fun RequestCallback<*>.reject() {
  onError(CONNECTION_CLOSED_MESSAGE)
}