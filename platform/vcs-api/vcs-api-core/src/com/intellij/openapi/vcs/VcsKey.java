// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs;

import com.intellij.openapi.util.IntellijInternalApi;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public final class VcsKey {
  private final @NotNull String myName;

  // to forbid creation outside AbstractVcs
  @ApiStatus.Internal
  @IntellijInternalApi
  public VcsKey(@NotNull @NonNls String name) {
    myName = name;
  }

  public @NonNls @NotNull String getName() {
    return myName;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    VcsKey vcsKey = (VcsKey)o;

    if (!myName.equals(vcsKey.myName)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    return myName.hashCode();
  }

  @Override
  public String toString() {
    return myName;
  }
}
