package org.jetbrains.rpc;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.concurrency.Promise;
import org.jetbrains.jsonProtocol.Request;

public interface MessageProcessor {
  void cancelWaitingRequests();

  void closed();

  @NotNull
  Promise<Void> send(@NotNull Request message);
}