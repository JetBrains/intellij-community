// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.tooling.internal.ear;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.model.ear.EarConfiguration;

import java.io.File;

/**
 * @author Vladislav.Soroka
 */
public class EarResourceImpl implements EarConfiguration.EarResource {
  private final @NotNull String myEarDirectory;
  private final @NotNull String myRelativePath;
  private final @NotNull File file;

  public EarResourceImpl(@NotNull String earDirectory, @NotNull String relativePath, @NotNull File file) {
    myEarDirectory = earDirectory;
    this.myRelativePath = relativePath;
    this.file = file;
  }

  @Override
  public @NotNull String getEarDirectory() {
    return myEarDirectory;
  }

  @Override
  public @NotNull String getRelativePath() {
    return myRelativePath;
  }

  @Override
  public @NotNull File getFile() {
    return file;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof EarResourceImpl)) return false;

    EarResourceImpl resource = (EarResourceImpl)o;
    if (!file.getPath().equals(resource.file.getPath())) return false;
    if (!myEarDirectory.equals(resource.myEarDirectory)) return false;
    if (!myRelativePath.equals(resource.myRelativePath)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = myEarDirectory.hashCode();
    result = 31 * result + myRelativePath.hashCode();
    result = 31 * result + file.getPath().hashCode();
    return result;
  }

  @Override
  public String toString() {
    return "EarResource{" +
           "earDirectory=" + myEarDirectory +
           ", warRelativePath='" + myRelativePath + '\'' +
           ", file=" + file +
           '}';
  }
}
