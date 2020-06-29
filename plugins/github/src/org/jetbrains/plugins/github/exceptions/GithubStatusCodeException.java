// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.exceptions;

import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.github.api.data.GithubErrorMessage;

public class GithubStatusCodeException extends GithubConfusingException {
  private final int myStatusCode;
  @Nullable private final GithubErrorMessage myError;

  public GithubStatusCodeException(@Nullable String message, int statusCode) {
    this(message, null, statusCode);
  }

  public GithubStatusCodeException(@Nullable String message, @Nullable GithubErrorMessage error, int statusCode) {
    super(message);
    myStatusCode = statusCode;
    myError = error;
  }

  public int getStatusCode() {
    return myStatusCode;
  }

  @Nullable
  public GithubErrorMessage getError() {
    return myError;
  }
}
