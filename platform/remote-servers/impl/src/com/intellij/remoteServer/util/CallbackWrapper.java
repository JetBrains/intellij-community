package com.intellij.remoteServer.util;

/**
 * @author michael.golubev
 */
public interface CallbackWrapper<T> {

  void onSuccess(T result);

  void onError(String message);
}
