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