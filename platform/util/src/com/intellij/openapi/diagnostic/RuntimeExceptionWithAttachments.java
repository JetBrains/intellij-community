// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.diagnostic;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@SuppressWarnings("ExceptionClassNameDoesntEndWithException")
public class RuntimeExceptionWithAttachments extends RuntimeException implements ExceptionWithAttachments {
  private final String myUserMessage;
  private final Attachment[] myAttachments;

  public RuntimeExceptionWithAttachments(@NotNull String message, Attachment @NotNull... attachments) {
    super(message);
    myUserMessage = null;
    myAttachments = attachments;
  }

  public RuntimeExceptionWithAttachments(@NotNull Throwable cause, Attachment @NotNull... attachments) {
    super(cause);
    myUserMessage = null;
    myAttachments = attachments;
  }

  public RuntimeExceptionWithAttachments(@NotNull String message, @Nullable Throwable cause, Attachment @NotNull... attachments) {
    super(message, cause);
    myUserMessage = null;
    myAttachments = attachments;
  }

  /**
   * Corresponds to {@link Logger#error(String, Throwable, Attachment...)}
   * ({@code LOG.error(userMessage, new RuntimeException(details), attachments)}).
   */
  public RuntimeExceptionWithAttachments(@NotNull String userMessage, @NotNull String details, Attachment @NotNull... attachments) {
    super(details);
    myUserMessage = userMessage;
    myAttachments = attachments;
  }

  @Nullable
  public String getUserMessage() {
    return myUserMessage;
  }

  @Override
  public Attachment @NotNull [] getAttachments() {
    return myAttachments;
  }
}