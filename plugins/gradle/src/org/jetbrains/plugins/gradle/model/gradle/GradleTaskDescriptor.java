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
package org.jetbrains.plugins.gradle.model.gradle;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.Serializable;

/**
 * @author Denis Zhdanov
 * @since 3/15/13 1:01 PM
 */
public class GradleTaskDescriptor implements Serializable, Comparable<GradleTaskDescriptor> {
  
  public enum Type { RUN, DEBUG, GENERAL }
  
  private static final long serialVersionUID = 1L;

  // The fields are mutable in order to ease IJ default xml serialization.
  private String myName;
  @NotNull private Type myType = Type.GENERAL;
  @Nullable private String myDescription;

  @SuppressWarnings("UnusedDeclaration")
  public GradleTaskDescriptor() {
    // Necessary for IJ serialization
  }

  public GradleTaskDescriptor(@NotNull String name, @Nullable String description) {
    setName(name);
    setDescription(description);
  }

  @Nullable
  public String getDescription() {
    return myDescription;
  }

  public String getName() {
    return myName;
  }

  public void setName(@NotNull String name) {
    myName = name;
  }

  @NotNull
  public Type getType() {
    return myType;
  }

  public void setType(@NotNull Type type) {
    myType = type;
  }

  public void setDescription(@Nullable String description) {
    myDescription = description;
  }

  @Override
  public int compareTo(GradleTaskDescriptor that) {
    int cmp = myName.compareTo(that.myName);
    if (cmp == 0) {
      cmp = myType.compareTo(that.myType);
    }
    return cmp;
  }

  @Override
  public int hashCode() {
    int result = myName.hashCode();
    result = 31 * result + myType.hashCode();
    result = 31 * result + (myDescription != null ? myDescription.hashCode() : 0);
    return result;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    GradleTaskDescriptor that = (GradleTaskDescriptor)o;

    if (!myName.equals(that.myName)) return false;
    if (myType != that.myType) return false;
    if (myDescription != null ? !myDescription.equals(that.myDescription) : that.myDescription != null) return false;

    return true;
  }

  @Override
  public String toString() {
    return myName;
  }
}
