package org.jetbrains.rpc;

public interface AsyncResultCallback<SUCCESS_RESPONSE, ERROR_DETAILS> {
  void onSuccess(SUCCESS_RESPONSE successResponse, ResultReader<SUCCESS_RESPONSE> resultReader);

  void onError(String errorMessage, ERROR_DETAILS errorDetails);
}