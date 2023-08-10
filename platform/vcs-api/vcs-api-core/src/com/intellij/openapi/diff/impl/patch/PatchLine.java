// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.openapi.diff.impl.patch;

import org.jetbrains.annotations.NotNull;

public class PatchLine {
  public enum Type {CONTEXT, ADD, REMOVE}

  @NotNull private final Type myType;
  @NotNull private final String myText;
  private boolean mySuppressNewLine;

  public PatchLine(@NotNull Type type, @NotNull String text) {
    myType = type;
    myText = text;
  }

  @NotNull
  public Type getType() {
    return myType;
  }

  @NotNull
  public String getText() {
    return myText;
  }

  public boolean isSuppressNewLine() {
    return mySuppressNewLine;
  }

  public void setSuppressNewLine(final boolean suppressNewLine) {
    mySuppressNewLine = suppressNewLine;
  }

  @Override
  public String toString() {
    return "PatchLine{" +
           "myType=" + myType +
           ", myText='" + myText + '\'' +
           ", mySuppressNewLine=" + mySuppressNewLine +
           '}';
  }
}
