// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.tooling;

import com.intellij.openapi.externalSystem.model.ExternalSystemException;
import org.gradle.api.Project;
import org.gradle.util.GradleVersion;
import org.jetbrains.annotations.*;

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
  private @Nullable Project myProject = null;

  @CheckReturnValue
  public @NotNull MessageBuilder withTitle(String title) {
    myTitle = title;
    return this;
  }

  @CheckReturnValue
  public @NotNull MessageBuilder withText(String text) {
    myText = text;
    return this;
  }

  @CheckReturnValue
  public @NotNull MessageBuilder withKind(Message.Kind kind) {
    myKind = kind;
    return this;
  }

  @CheckReturnValue
  public @NotNull MessageBuilder withGroup(String group) {
    myGroup = group;
    return this;
  }

  @CheckReturnValue
  public @NotNull MessageBuilder withException(Exception e) {
    myException = e;
    return this;
  }

  @CheckReturnValue
  public @NotNull MessageBuilder withLocation(Message.FilePosition filePosition) {
    myFilePosition = filePosition;
    return this;
  }

  @CheckReturnValue
  public @NotNull MessageBuilder withLocation(String filePath, int line, int column) {
    return withLocation(new Message.FilePosition(filePath, line, column));
  }

  @CheckReturnValue
  public @NotNull MessageBuilder withProject(Project project) {
    myProject = project;
    return this;
  }

  private @NotNull String buildTitle() {
    assert myTitle != null;

    String title = myTitle;
    if (myProject != null) {
      String projectDisplayName = getProjectDisplayName(myProject);
      title = projectDisplayName + ": " + title;
    }
    return title;
  }

  private @NotNull String buildText() {
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
    return text;
  }

  private @Nullable Message.FilePosition buildFilePosition() {
    Message.FilePosition filePosition = myFilePosition;
    if (filePosition == null && myProject != null) {
      String buildScriptPath = myProject.getBuildFile().getPath();
      filePosition = new Message.FilePosition(buildScriptPath, 0, 0);
    }
    return filePosition;
  }

  public @NotNull Message build() {
    String title = buildTitle();
    String text = buildText();
    Message.FilePosition filePosition = buildFilePosition();
    return new Message(title, text, myGroup, myKind, filePosition);
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

  @NotNull
  private static String getProjectDisplayName(@NotNull Project project) {
    String projectDisplayName;
    if (GradleVersion.current().getBaseVersion().compareTo(GradleVersion.version("3.3")) < 0) {
      StringBuilder builder = new StringBuilder();
      if (project.getParent() == null && project.getGradle().getParent() == null) {
        builder.append("root project '");
        builder.append(project.getName());
        builder.append('\'');
      }
      else {
        builder.append("project '");
        builder.append(project.getPath());
        builder.append("'");
      }
      projectDisplayName = builder.toString();
    }
    else {
      projectDisplayName = project.getDisplayName();
    }
    return projectDisplayName;
  }
}
