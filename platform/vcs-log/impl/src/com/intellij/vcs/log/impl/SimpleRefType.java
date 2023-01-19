// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.impl;

import com.intellij.vcs.log.VcsRefType;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.util.Objects;

public class SimpleRefType implements VcsRefType {
  private final @NotNull @NonNls String myName;
  private final boolean myIsBranch;
  private final @NotNull Color myColor;

  public SimpleRefType(@NotNull @NonNls String name, boolean isBranch, @NotNull Color color) {
    myName = name;
    myIsBranch = isBranch;
    myColor = color;
  }

  @Override
  public boolean isBranch() {
    return myIsBranch;
  }

  @Override
  public @NotNull Color getBackgroundColor() {
    return myColor;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    SimpleRefType type = (SimpleRefType)o;
    return myIsBranch == type.myIsBranch && Objects.equals(myName, type.myName);
  }

  @Override
  public int hashCode() {
    return Objects.hash(myName, myIsBranch);
  }

  @Override
  public String toString() {
    return myName;
  }
}
