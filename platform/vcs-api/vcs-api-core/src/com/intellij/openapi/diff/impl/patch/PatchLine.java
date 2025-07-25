// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.openapi.diff.impl.patch;

import org.jetbrains.annotations.NotNull;

public class PatchLine {
  public enum Type {CONTEXT, ADD, REMOVE}
  public static final int UNKNOWN_PATCH_FILE_LINE_NUMBER = -1;

  private final @NotNull Type myType;
  private final @NotNull String myText;
  private boolean mySuppressNewLine;
  private final int myPatchFileLineNumber;

  public PatchLine(@NotNull Type type, @NotNull String text) {
    this(type, text, UNKNOWN_PATCH_FILE_LINE_NUMBER);
  }

  public PatchLine(@NotNull Type type, @NotNull String text, int patchFileLineNumber) {
    myType = type;
    myText = text;
    myPatchFileLineNumber = patchFileLineNumber;
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

  public int getPatchFileLineNumber() {
    return myPatchFileLineNumber;
  }

  @Override
  public String toString() {
    return "PatchLine{" +
           "myType=" + myType +
           ", myText='" + myText + '\'' +
           ", mySuppressNewLine=" + mySuppressNewLine +
           ", myPatchFileLineNumber=" + myPatchFileLineNumber +
           '}';
  }
}
