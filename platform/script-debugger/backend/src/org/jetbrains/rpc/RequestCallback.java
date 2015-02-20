package org.jetbrains.rpc;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface RequestCallback<SUCCESS_RESPONSE> {
  void onSuccess(@Nullable SUCCESS_RESPONSE successResponse, @Nullable ResultReader<SUCCESS_RESPONSE> resultReader);

  void onError(@NotNull Throwable error);
}