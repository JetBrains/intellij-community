// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.impl;

import com.intellij.vcs.log.VcsRefType;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.util.Objects;

public class SimpleRefType implements VcsRefType {
  @NotNull private final String myName;
  private final boolean myIsBranch;
  @NotNull private final Color myColor;

  public SimpleRefType(@NotNull String name, boolean isBranch, @NotNull Color color) {
    myName = name;
    myIsBranch = isBranch;
    myColor = color;
  }

  @Override
  public boolean isBranch() {
    return myIsBranch;
  }

  @NotNull
  @Override
  public Color getBackgroundColor() {
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
