package org.jetbrains.rpc;

import com.intellij.util.BooleanFunction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jsonProtocol.Request;

public abstract class MessageWriter implements BooleanFunction<Request> {
  @Override
  public boolean fun(@NotNull Request message) {
    CharSequence content = message.toJson();
    if (isDebugLoggingEnabled()) {
      CommandProcessor.LOG.debug("OUT: " + content.toString());
    }
    return write(content);
  }

  protected boolean isDebugLoggingEnabled() {
    return CommandProcessor.LOG.isDebugEnabled();
  }

  protected abstract boolean write(@NotNull CharSequence content);
}