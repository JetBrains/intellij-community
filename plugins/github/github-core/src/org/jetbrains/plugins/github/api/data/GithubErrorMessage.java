// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.api.data;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.github.i18n.GithubBundle;

import java.util.Collections;
import java.util.List;

@SuppressWarnings("UnusedDeclaration")
public class GithubErrorMessage {
  private @Nullable String message;
  private @Nullable List<Error> errors;

  public @Nullable String getMessage() {
    return message;
  }

  public @Nullable List<Error> getErrors() {
    if (errors == null) return Collections.emptyList();
    return errors;
  }

  public @NotNull String getPresentableError() {
    if (errors == null) {
      return message != null ? message : GithubBundle.message("unknown.loading.error");
    }
    else {
      StringBuilder s = new StringBuilder();
      if (message != null) s.append(message);
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

    public @Nullable String getResource() {
      return resource;
    }

    public @Nullable String getField() {
      return field;
    }

    public @Nullable String getCode() {
      return code;
    }

    public @Nullable String getMessage() {
      return message;
    }
  }
}

