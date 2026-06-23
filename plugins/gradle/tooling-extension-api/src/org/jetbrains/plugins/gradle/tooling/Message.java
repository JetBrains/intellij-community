// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.tooling;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@ApiStatus.Experimental
public final class Message {
  private final @NotNull String myTitle;
  private final @NotNull String myText;
  private final @Nullable String myGroup;
  private final @NotNull Kind myKind;
  private final @Nullable String myTargetPath;
  private final @Nullable Failure myFailure;

  private final boolean myInternal;

  public Message(
    @NotNull String title,
    @NotNull String text,
    @Nullable String group,
    @NotNull Kind kind,
    @Nullable String targetPath,
    @Nullable Failure failure,
    boolean isInternal
  ) {
    myTitle = title;
    myText = text;
    myGroup = group;
    myInternal = isInternal;
    myKind = kind;
    myTargetPath = targetPath;
    myFailure = failure;
  }

  public @NotNull String getTitle() {
    return myTitle;
  }

  public @NotNull String getText() {
    return myText;
  }

  public @Nullable String getGroup() {
    return myGroup;
  }

  public @NotNull Kind getKind() {
    return myKind;
  }

  public @Nullable String getTargetPath() {
    return myTargetPath;
  }

  public @Nullable Failure getFailure() {
    return myFailure;
  }

  public boolean isInternal() {
    return myInternal;
  }

  public enum Kind {
    ERROR,
    WARNING,
    INFO
  }

  public static final class Failure {
    private final @Nullable String myMessage;
    private final @Nullable String myDescription;
    private final @Nullable List<Failure> myCauses;

    public Failure(
      @Nullable String message,
      @Nullable String description
    ) {
      this(message, description, Collections.emptyList());
    }

    public Failure(
      @Nullable String message,
      @Nullable String description,
      @NotNull List<Failure> causes
    ) {
      myMessage = message;
      myDescription = description;
      myCauses = Collections.unmodifiableList(new ArrayList<>(causes));
    }

    public @Nullable String getMessage() {
      return myMessage;
    }

    public @Nullable String getDescription() {
      return myDescription;
    }

    public @NotNull List<Failure> getCauses() {
      return myCauses == null ? Collections.emptyList() : myCauses;
    }
  }
}
