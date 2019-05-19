// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.api.data;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

@SuppressWarnings("UnusedDeclaration")
public class GithubErrorMessage {
  private String message;
  private List<Error> errors;

  @NotNull
  public String getMessage() {
    return message;
  }

  @NotNull
  public List<Error> getErrors() {
    if (errors == null) return Collections.emptyList();
    return errors;
  }

  @NotNull
  public String getPresentableError() {
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

  public static class Error {
    private String resource;
    private String field;
    private String code;
    private String message;

    @NotNull
    public String getResource() {
      return resource;
    }

    @Nullable
    public String getField() {
      return field;
    }

    @NotNull
    public String getCode() {
      return code;
    }

    @Nullable
    public String getMessage() {
      return message;
    }
  }
}

