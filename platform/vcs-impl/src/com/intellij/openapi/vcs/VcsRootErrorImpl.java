// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs;

import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * @author Nadya Zabrodina
 */
public class VcsRootErrorImpl implements VcsRootError {

  private final @NotNull Type myType;
  private final @NotNull VcsDirectoryMapping myMapping;

  public VcsRootErrorImpl(@NotNull Type type, @NotNull VcsDirectoryMapping mapping) {
    myType = type;
    myMapping = mapping;
  }

  @Override
  @NotNull
  public Type getType() {
    return myType;
  }

  @Override
  @NotNull
  public VcsDirectoryMapping getMapping() {
    return myMapping;
  }

  @Override
  public String toString() {
    return String.format("VcsRootError{%s - %s}", myType, myMapping); //NON-NLS
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    VcsRootError error = (VcsRootError)o;

    if (!Objects.equals(myMapping, error.getMapping())) return false;
    if (myType != error.getType()) return false;

    return true;
  }

  @Override
  public int hashCode() {
    return Objects.hash(myType, myMapping);
  }
}