// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.tooling;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;

@ApiStatus.Experimental
public final class Message {
  private final @NotNull String myTitle;
  private final @NotNull String myText;
  private final @Nullable String myGroup;
  private final @NotNull Kind myKind;
  private final @Nullable String myTargetPath;

  private final boolean myInternal;

  public Message(
    @NotNull String title,
    @NotNull String text,
    @Nullable String group,
    @NotNull Kind kind,
    @Nullable String targetPath,
    boolean isInternal
  ) {
    myTitle = title;
    myText = text;
    myGroup = group;
    myInternal = isInternal;
    myKind = kind;
    myTargetPath = targetPath;
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

  public boolean isInternal() {
    return myInternal;
  }

  public enum Kind {
    ERROR,
    WARNING,
    INFO
  }
}
