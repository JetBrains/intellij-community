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

import java.io.IOException;

/**
 * @author Aleksey Pivovarov
 */
public class GithubConfusingException extends IOException {
  private String myDetails;

  public GithubConfusingException() {
  }

  public GithubConfusingException(String message) {
    super(message);
  }

  public GithubConfusingException(String message, Throwable cause) {
    super(message, cause);
  }

  public GithubConfusingException(Throwable cause) {
    super(cause);
  }

  public void setDetails(@Nullable String details) {
    myDetails = details;
  }

  @Override
  public String getMessage() {
    if (myDetails == null) {
      return super.getMessage();
    }
    else {
      return myDetails + "\n\n" + super.getMessage();
    }
  }
}
