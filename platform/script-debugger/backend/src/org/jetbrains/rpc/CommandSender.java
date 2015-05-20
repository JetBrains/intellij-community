package org.jetbrains.rpc;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.concurrency.Promise;
import org.jetbrains.jsonProtocol.Request;

public interface CommandSender {
  @NotNull
  <RESULT> Promise<RESULT> send(@NotNull Request<RESULT> message);
}