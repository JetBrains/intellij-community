/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package git4idea.config;

import org.jetbrains.annotations.Nls;

public class GitVersionIdentificationException extends RuntimeException {
  public GitVersionIdentificationException(@Nls String message, Throwable cause) {
    super(message, cause);
  }
}
