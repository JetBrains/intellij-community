// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vfs.newvfs.persistent;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

public final class SemanticLabel {
  private final @NotNull String myName;
  private final @NotNull String myType;
  private final @Nullable String myVersion;

  public SemanticLabel(@NotNull String name, @NotNull String type, @Nullable String version) {
    myName = name;
    myType = type;
    myVersion = version;
  }

  public @NotNull String getType() {
    return myType;
  }

  public @NotNull String getName() {
    return myName;
  }

  public @Nullable String getVersion() {
    return myVersion;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    SemanticLabel label = (SemanticLabel)o;
    return myName.equals(label.myName) && myType.equals(label.myType) && Objects.equals(myVersion, label.myVersion);
  }

  @Override
  public int hashCode() {
    return Objects.hash(myName, myType, myVersion);
  }

  @Override
  public String toString() {
    return "SemanticLabel(" + myName + ":" + myType + ":" + myVersion + ")";
  }
}
