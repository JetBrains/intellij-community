package org.jetbrains.rpc;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface RequestCallback<SUCCESS_RESPONSE, ERROR_DETAILS> {
  void onSuccess(SUCCESS_RESPONSE successResponse, @Nullable ResultReader<SUCCESS_RESPONSE> resultReader);

  void onError(@NotNull String errorMessage, @Nullable ERROR_DETAILS errorDetails);
}