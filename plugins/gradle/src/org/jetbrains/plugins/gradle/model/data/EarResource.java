// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.model.data;

import com.intellij.serialization.PropertyMapping;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.Serializable;

/**
 * @author Vladislav.Soroka
 */
public class EarResource implements Serializable {
  private static final long serialVersionUID = 1L;

  private final @NotNull String earDirectory;
  private final @NotNull String relativePath;
  private final @NotNull File file;

  @PropertyMapping({"earDirectory", "relativePath", "file"})
  public EarResource(@NotNull String earDirectory, @NotNull String relativePath, @NotNull File file) {
    this.earDirectory = earDirectory;
    this.relativePath = getAdjustedPath(relativePath);
    this.file = file;
  }

  public @NotNull String getEarDirectory() {
    return earDirectory;
  }

  public @NotNull String getRelativePath() {
    return relativePath;
  }

  public @NotNull File getFile() {
    return file;
  }

  private static String getAdjustedPath(final @NotNull String path) {
    return path.isEmpty() || path.charAt(0) != '/' ? '/' + path : path;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof EarResource resource)) return false;

    if (!file.getPath().equals(resource.file.getPath())) return false;
    if (!earDirectory.equals(resource.earDirectory)) return false;
    if (!relativePath.equals(resource.relativePath)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = earDirectory.hashCode();
    result = 31 * result + relativePath.hashCode();
    result = 31 * result + file.getPath().hashCode();
    return result;
  }

  @Override
  public String toString() {
    return "Resource{" +
           "earDirectory=" + earDirectory +
           ", relativePath='" + relativePath + '\'' +
           ", file=" + file +
           '}';
  }
}
