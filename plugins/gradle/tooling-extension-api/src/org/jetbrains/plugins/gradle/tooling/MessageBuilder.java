// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.tooling;

import com.intellij.openapi.externalSystem.model.ExternalSystemException;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.PrintWriter;
import java.io.StringWriter;

import static com.intellij.util.ExceptionUtilRt.findCause;

@ApiStatus.Internal
public final class MessageBuilder {

  private @Nullable String myTitle = null;
  private @Nullable String myText = null;
  private @Nullable String myGroup = null;
  private @Nullable Exception myException = null;
  private @NotNull Message.Kind myKind = Message.Kind.INFO;
  private @Nullable Message.FilePosition myFilePosition = null;

  public @NotNull MessageBuilder withTitle(String title) {
    myTitle = title;
    return this;
  }

  public @NotNull MessageBuilder withText(String text) {
    myText = text;
    return this;
  }

  public @NotNull MessageBuilder info() { return withKind(Message.Kind.INFO); }

  public @NotNull MessageBuilder warning() { return withKind(Message.Kind.WARNING); }

  public @NotNull MessageBuilder error() { return withKind(Message.Kind.ERROR); }

  public @NotNull MessageBuilder withKind(Message.Kind kind) {
    myKind = kind;
    return this;
  }

  public @NotNull MessageBuilder withGroup(String group) {
    myGroup = group;
    return this;
  }

  public @NotNull MessageBuilder withException(Exception e) {
    myException = e;
    return this;
  }

  public @NotNull MessageBuilder withLocation(String filePath, int line, int column) {
    myFilePosition = new Message.FilePosition(filePath, line, column);
    return this;
  }

  public @NotNull Message build() {
    assert myTitle != null;
    assert myText != null;

    String text = myText;
    if (myException != null) {
      if (myException.getStackTrace().length > 0) {
        text += ("\n\n" + getErrorMessage(myException));
      }
      else {
        text += ("\n\n" + myException.getMessage());
      }
    }
    return new Message(myTitle, text, myGroup, myKind, myFilePosition);
  }

  @Contract("null -> null; !null->!null")
  private static String getErrorMessage(@Nullable Throwable e) {
    if (e == null) return null;
    StringWriter sw = new StringWriter();
    e.printStackTrace(new PrintWriter(sw));
    ExternalSystemException esException = findCause(e, ExternalSystemException.class);
    if (esException != null && esException != e) {
      sw.append("\nCaused by: ").append(esException.getOriginalReason());
    }
    return sw.toString();
  }
}
