// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs;

import org.jetbrains.annotations.NotNull;

/**
 * @author Nadya Zabrodina
 */
public class VcsRootErrorImpl implements VcsRootError {

  private final @NotNull Type myType;
  private final @NotNull String myMapping;
  private final @NotNull VcsKey myVcsKey;


  public VcsRootErrorImpl(@NotNull Type type, @NotNull String mapping, @NotNull String key) {
    myType = type;
    myMapping = mapping;
    myVcsKey = new VcsKey(key);
  }

  @Override
  @NotNull
  public Type getType() {
    return myType;
  }

  @Override
  @NotNull
  public String getMapping() {
    return myMapping;
  }

  @Override
  @NotNull
  public VcsKey getVcsKey() {
    return myVcsKey;
  }

  @Override
  public String toString() {
    return String.format("VcsRootError{%s - %s}", myType, myMapping);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    VcsRootError error = (VcsRootError)o;

    if (!myMapping.equals(error.getMapping())) return false;
    if (myType != error.getType()) return false;

    return true;
  }

  @SuppressWarnings("ConstantConditions")
  @Override
  public int hashCode() {
    int result = myType != null ? myType.hashCode() : 0;
    result = 31 * result + myMapping.hashCode();
    return result;
  }
}