package org.jetbrains.rpc;

import com.intellij.openapi.util.ActionCallback;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jsonProtocol.Request;

public interface MessageProcessor {
  void cancelWaitingRequests();

  void closed();

  ActionCallback send(@NotNull Request message);
}