package org.jetbrains.rpc;

import com.intellij.openapi.util.ActionCallback;

class CommandCallbackWithoutResponse<SUCCESS_RESPONSE, ERROR_DETAILS> extends AsyncResultCallbackBase<SUCCESS_RESPONSE, ActionCallback, ERROR_DETAILS> {
  protected CommandCallbackWithoutResponse() {
    super(new ActionCallback(), null);
  }

  @Override
  public void onSuccess(SUCCESS_RESPONSE response, ResultReader<SUCCESS_RESPONSE> resultReader) {
    callback.setDone();
  }
}