// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.project;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * @author Dmitry Avdeev
 */
public final class ProjectType {

  private @Nullable String id;

  /**
   * For serialization.
   */
  public ProjectType() {
  }

  public ProjectType(@NotNull String id) {
    this.id = id;
  }

  public @Nullable String getId() {
    return id;
  }

  public void setId(@Nullable String id) {
    this.id = id;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    ProjectType type = (ProjectType)o;
    return Objects.equals(id, type.id);
  }

  @Override
  public int hashCode() {
    return id != null ? id.hashCode() : 0;
  }

  public static @Nullable ProjectType create(@Nullable String id) {
    return id != null ? new ProjectType(id) : null;
  }
}
