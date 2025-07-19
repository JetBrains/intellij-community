// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.openapi.diff.impl.patch;

import org.jetbrains.annotations.NotNull;

public class PatchLine {
  public enum Type {CONTEXT, ADD, REMOVE}

  private final @NotNull Type myType;
  private final @NotNull String myText;
  private boolean mySuppressNewLine;
  private final int myOriginalLineNumber;

  public PatchLine(@NotNull Type type, @NotNull String text) {
    this(type, text, -1);
  }

  public PatchLine(@NotNull Type type, @NotNull String text, int originalLineNumber) {
    myType = type;
    myText = text;
    myOriginalLineNumber = originalLineNumber;
  }

  public @NotNull Type getType() {
    return myType;
  }

  public @NotNull String getText() {
    return myText;
  }

  public boolean isSuppressNewLine() {
    return mySuppressNewLine;
  }

  public void setSuppressNewLine(final boolean suppressNewLine) {
    mySuppressNewLine = suppressNewLine;
  }

  public int getOriginalLineNumber() {
    return myOriginalLineNumber;
  }

  @Override
  public String toString() {
    return "PatchLine{" +
           "myType=" + myType +
           ", myText='" + myText + '\'' +
           ", mySuppressNewLine=" + mySuppressNewLine +
           ", myOriginalLineNumber=" + myOriginalLineNumber +
           '}';
  }
}
