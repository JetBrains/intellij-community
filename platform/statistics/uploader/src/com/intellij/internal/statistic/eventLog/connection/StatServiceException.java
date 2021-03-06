// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.eventLog.connection;


public class StatServiceException extends RuntimeException {

  public StatServiceException(String s) {
    super(s);
  }

  public StatServiceException(String s, Throwable throwable) {
    super(s, throwable);
  }
}
