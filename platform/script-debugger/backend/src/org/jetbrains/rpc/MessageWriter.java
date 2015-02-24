package org.jetbrains.rpc;

import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.util.BooleanFunction;
import io.netty.buffer.ByteBuf;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jsonProtocol.Request;

import static org.jetbrains.rpc.CommandProcessor.LOG;

public abstract class MessageWriter implements BooleanFunction<Request> {
  @Override
  public boolean fun(@NotNull Request message) {
    ByteBuf content = message.getBuffer();
    if (isDebugLoggingEnabled()) {
      LOG.debug("OUT: " + content.toString(CharsetToolkit.UTF8_CHARSET));
    }
    return write(content);
  }

  protected boolean isDebugLoggingEnabled() {
    return LOG.isDebugEnabled();
  }

  protected abstract boolean write(@NotNull ByteBuf content);
}