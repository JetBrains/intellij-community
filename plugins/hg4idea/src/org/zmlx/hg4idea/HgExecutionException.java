// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.zmlx.hg4idea;

public class HgExecutionException extends RuntimeException {

  public HgExecutionException(String message, Throwable cause) {
    super(message, cause);
  }
}