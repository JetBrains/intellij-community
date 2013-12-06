/*
 * Copyright 2000-2013 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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

  @NotNull
  public Type getType() {
    return myType;
  }

  @NotNull
  public String getMapping() {
    return myMapping;
  }

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