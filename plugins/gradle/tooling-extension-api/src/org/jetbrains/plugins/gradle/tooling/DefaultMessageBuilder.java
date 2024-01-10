// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.tooling;

import com.intellij.openapi.externalSystem.model.ExternalSystemException;
import com.intellij.openapi.util.text.StringUtilRt;
import org.gradle.api.Project;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.PrintWriter;
import java.io.StringWriter;

import static com.intellij.util.ExceptionUtilRt.findCause;

@ApiStatus.Internal
public final class DefaultMessageBuilder implements MessageBuilder {

  private @Nullable String myTitle = null;
  private @Nullable String myText = null;
  private @Nullable String myGroup = null;
  private @Nullable Exception myException = null;
  private @Nullable Message.Kind myKind = null;
  private @Nullable Message.FilePosition myFilePosition = null;
  private @Nullable Project myProject = null;

  @Override
  public @NotNull MessageBuilder withTitle(String title) {
    myTitle = title;
    return this;
  }

  @Override
  public @NotNull MessageBuilder withText(String text) {
    myText = text;
    return this;
  }

  @Override
  public @NotNull MessageBuilder withKind(Message.Kind kind) {
    myKind = kind;
    return this;
  }

  @Override
  public @NotNull MessageBuilder withGroup(String group) {
    myGroup = group;
    return this;
  }

  @Override
  public @NotNull MessageBuilder withException(Exception e) {
    myException = e;
    return this;
  }

  @Override
  public @NotNull MessageBuilder withLocation(Message.FilePosition filePosition) {
    myFilePosition = filePosition;
    return this;
  }

  @Override
  public @NotNull MessageBuilder withLocation(String filePath, int line, int column) {
    return withLocation(new Message.FilePosition(filePath, line, column));
  }

  @Override
  public @NotNull MessageBuilder withProject(Project project) {
    myProject = project;
    return this;
  }

  @Override
  public @NotNull Message build() {
    String title = buildTitle();
    String text = buildText();
    String group = buildGroup();
    Message.Kind kind = buildKind();
    Message.FilePosition filePosition = buildFilePosition();
    return new Message(title, text, group, kind, filePosition);
  }

  private @NotNull String buildTitle() {
    String title = myTitle;
    if (title == null && myException != null) {
      title = getRootCauseExceptionMessage(myException);
    }
    if (title == null) {
      title = myText;
    }
    if (title == null) {
      assert myGroup != null;
      title = myGroup;
    }
    if (myProject != null) {
      String projectDisplayName = myProject.getDisplayName();
      title = projectDisplayName + ": " + title;
    }
    return title;
  }

  private @NotNull String buildText() {
    String text = myText;
    if (myException != null) {
      if (text == null) {
        text = buildExceptionStacktrace(myException);
      }
      else {
        text += "\n\n" + buildExceptionStacktrace(myException);
      }
    }
    if (text == null) {
      text = myTitle;
    }
    if (text == null) {
      assert myGroup != null;
      text = myGroup;
    }
    return text;
  }

  private @NotNull String buildGroup() {
    assert myGroup != null;
    return myGroup;
  }

  private @NotNull Message.Kind buildKind() {
    Message.Kind kind = myKind;
    if (kind == null) {
      kind = Message.Kind.INFO;
    }
    return kind;
  }

  private @Nullable Message.FilePosition buildFilePosition() {
    Message.FilePosition filePosition = myFilePosition;
    if (filePosition == null && myProject != null) {
      String buildScriptPath = myProject.getBuildFile().getPath();
      filePosition = new Message.FilePosition(buildScriptPath, 0, 0);
    }
    return filePosition;
  }

  private static @NotNull String buildExceptionStacktrace(@NotNull Throwable exception) {
    if (exception.getStackTrace().length == 0) {
      return getRootCauseExceptionMessage(exception);
    }
    return getExceptionStacktrace(exception);
  }

  private static @Nullable String getExceptionOriginalReason(@NotNull Throwable exception) {
    ExternalSystemException esException = findCause(exception, ExternalSystemException.class);
    if (esException != null && esException != exception) {
      String originalReason = esException.getOriginalReason();
      if (!StringUtilRt.isEmptyOrSpaces(originalReason)) {
        return originalReason;
      }
    }
    return null;
  }

  private static @NotNull String getExceptionStacktrace(@NotNull Throwable exception) {
    StringWriter sw = new StringWriter();
    exception.printStackTrace(new PrintWriter(sw));
    String originalReason = getExceptionOriginalReason(exception);
    if (originalReason != null) {
      sw.append("\nCaused by: ").append(originalReason);
    }
    return sw.toString();
  }

  private static @NotNull String getRootCauseExceptionMessage(@NotNull Throwable exception) {
    Throwable rootCauseException = exception;
    while (rootCauseException.getCause() != null) {
      rootCauseException = rootCauseException.getCause();
    }
    String message = rootCauseException.getMessage();
    if (message == null) {
      message = exception.getMessage();
    }
    if (message == null) {
      message = exception.getClass().getName();
    }
    return message;
  }
}
