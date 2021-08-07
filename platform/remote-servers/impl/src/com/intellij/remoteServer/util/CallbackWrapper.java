package com.intellij.remoteServer.util;

import org.jetbrains.annotations.NotNull;

/**
 * @author michael.golubev
 */
public interface CallbackWrapper<T> {

  void onSuccess(T result);

  void onError(@NotNull String message);
}
