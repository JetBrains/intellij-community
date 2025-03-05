// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.tooling;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Experimental
public final class Message {
  private final @NotNull String myTitle;
  private final @NotNull String myText;
  private final @Nullable String myGroup;
  private final @NotNull Kind myKind;
  private final @Nullable FilePosition myFilePosition;

  private final boolean myInternal;

  public Message(
    @NotNull String title,
    @NotNull String text,
    @Nullable String group,
    @NotNull Kind kind,
    @Nullable FilePosition filePosition,
    boolean isInternal
  ) {
    myTitle = title;
    myText = text;
    myGroup = group;
    myInternal = isInternal;
    myKind = kind;
    myFilePosition = filePosition;
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

  public @Nullable FilePosition getFilePosition() {
    return myFilePosition;
  }

  public boolean isInternal() {
    return myInternal;
  }

  public static class FilePosition {
    private final @NotNull String myFilePath;
    private final int myLine;
    private final int myColumn;

    public FilePosition(@NotNull String filePath, int line, int column) {
      myFilePath = filePath;
      myLine = line;
      myColumn = column;
    }

    public @NotNull String getFilePath() {
      return myFilePath;
    }

    public int getLine() {
      return myLine;
    }

    public int getColumn() {
      return myColumn;
    }
  }

  public enum Kind {
    ERROR,
    WARNING,
    INFO
  }
}
