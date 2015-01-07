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
package org.jetbrains.plugins.github.api;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @author Aleksey Pivovarov
 */
@SuppressWarnings("UnusedDeclaration")
public class GithubErrorMessage {
  @Nullable public String message;
  @Nullable public List<Error> errors;

  public static class Error {
    @Nullable public String resource;
    @Nullable public String field;
    @Nullable public String code;
    @Nullable public String message;
  }

  @Nullable
  public String getMessage() {
    if (errors == null) {
      return message;
    }
    else {
      StringBuilder s = new StringBuilder();
      s.append(message);
      for (Error e : errors) {
        s.append(String.format("<br/>[%s; %s]%s: %s", e.resource, e.field, e.code, e.message));
      }
      return s.toString();
    }
  }

  public boolean containsReasonMessage(@NotNull String reason) {
    if (message == null) return false;
    return message.contains(reason);
  }

  public boolean containsErrorCode(@NotNull String code) {
    if (errors == null) return false;
    for (Error error : errors) {
      if (error.code != null && error.code.contains(code)) return true;
    }
    return false;
  }

  public boolean containsErrorMessage(@NotNull String message) {
    if (errors == null) return false;
    for (Error error : errors) {
      if (error.code != null && error.code.contains(message)) return true;
    }
    return false;
  }
}

