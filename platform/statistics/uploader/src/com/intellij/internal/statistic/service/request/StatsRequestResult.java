// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.service.request;

public class StatsRequestResult<T> {
  private final T myResult;
  private final int myErrorCode;

  private StatsRequestResult(T result, int errorCode) {
    myResult = result;
    myErrorCode = errorCode;
  }

  public static <T> StatsRequestResult<T> succeed(T result) {
    return new StatsRequestResult<>(result, -1);
  }

  public static <T> StatsRequestResult<T> error(int error) {
    return new StatsRequestResult<>(null, error);
  }

  public T getResult() {
    return myResult;
  }

  public int getError() {
    return myErrorCode;
  }

  public boolean isSucceed() {
    return myResult != null;
  }
}
