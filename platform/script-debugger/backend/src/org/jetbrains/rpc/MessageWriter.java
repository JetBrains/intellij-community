/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

import com.intellij.openapi.vfs.CharsetToolkit
import com.intellij.util.BooleanFunction
import io.netty.buffer.ByteBuf
import org.jetbrains.jsonProtocol.Request

abstract class MessageWriter : BooleanFunction<Request<Any>> {
  override fun `fun`(message: Request<Any>): Boolean {
    val content = message.buffer
    if (isDebugLoggingEnabled) {
      LOG.debug("OUT: ${content.toString(CharsetToolkit.UTF8_CHARSET)}")
    }
    return write(content)
  }

  protected open val isDebugLoggingEnabled: Boolean
    get() = LOG.isDebugEnabled

  protected abstract fun write(content: ByteBuf): Boolean
}