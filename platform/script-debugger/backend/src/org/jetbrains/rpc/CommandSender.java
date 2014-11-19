package org.jetbrains.rpc;

import com.intellij.openapi.util.AsyncResult;
import com.intellij.util.Function;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.concurrency.Promise;
import org.jetbrains.jsonProtocol.Request;
import org.jetbrains.jsonProtocol.RequestWithResponse;

public interface CommandSender<ERROR_DETAILS> {
  @NotNull
  Promise<Void> send(@NotNull Request message);

  <RESULT> Promise<RESULT> send(@NotNull RequestWithResponse<RESULT> message);

  <RESULT, TRANSFORMED_RESULT> AsyncResult<TRANSFORMED_RESULT> send(@NotNull RequestWithResponse message,
                                                                    @NotNull Function<RESULT, TRANSFORMED_RESULT> transform,
                                                                    @Nullable ErrorConsumer<AsyncResult<TRANSFORMED_RESULT>, ERROR_DETAILS> errorConsumer);
}