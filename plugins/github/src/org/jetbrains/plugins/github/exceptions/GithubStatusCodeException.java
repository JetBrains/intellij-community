/*
 * Copyright 2000-2013 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.plugins.github.exceptions;

import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.github.api.GithubErrorMessage;

/**
 * @author Aleksey Pivovarov
 */
public class GithubStatusCodeException extends GithubConfusingException {
  private final int myStatusCode;
  private final GithubErrorMessage myError;

  public GithubStatusCodeException(String message, int statusCode) {
    this(message, null, statusCode);
  }

  public GithubStatusCodeException(String message, GithubErrorMessage error, int statusCode) {
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
