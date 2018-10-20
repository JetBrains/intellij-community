// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.exceptions;

import org.jetbrains.annotations.NotNull;

public class GithubParseException extends RuntimeException {
  public GithubParseException(@NotNull String message) {
    super(message);
  }
}
