package org.jetbrains.rpc;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface AsyncResultCallback<SUCCESS_RESPONSE, ERROR_DETAILS> {
  void onSuccess(SUCCESS_RESPONSE successResponse, @NotNull ResultReader<SUCCESS_RESPONSE> resultReader);

  void onError(@NotNull String errorMessage, @Nullable ERROR_DETAILS errorDetails);
}