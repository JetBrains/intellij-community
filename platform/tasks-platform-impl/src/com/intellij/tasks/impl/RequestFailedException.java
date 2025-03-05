// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tasks.impl;

import com.intellij.tasks.TaskBundle;
import com.intellij.tasks.TaskRepository;
import org.jetbrains.annotations.NotNull;

/**
 * @author Mikhail Golubev
 */
public class RequestFailedException extends RuntimeException {

  private TaskRepository myRepository;

  public static @NotNull RequestFailedException forStatusCode(int code, @NotNull String message) {
    return new RequestFailedException(TaskBundle.message("failure.http.error", code, message));
  }

  public static @NotNull RequestFailedException forServerMessage(@NotNull String message) {
    return new RequestFailedException(TaskBundle.message("failure.server.message", message));
  }

  public RequestFailedException(TaskRepository repository, String message) {
    super(message);
    myRepository = repository;
  }

  public RequestFailedException(String message) {
    super(message);
  }

  public RequestFailedException(String message, Throwable cause) {
    super(message, cause);
  }

  public RequestFailedException(Throwable cause) {
    super(cause);
  }

  public TaskRepository getRepository() {
    return myRepository;
  }
}
