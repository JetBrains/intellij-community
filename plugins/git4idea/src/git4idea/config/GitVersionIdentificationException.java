/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package git4idea.config;

public class GitVersionIdentificationException extends RuntimeException {
  public GitVersionIdentificationException(String message, Throwable cause) {
    super(message, cause);
  }
}
