// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.model.data;

import com.intellij.serialization.PropertyMapping;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.Serializable;

/**
 * @author Vladislav.Soroka
 */
public class WebResource implements Serializable {
  private static final long serialVersionUID = 1L;

  private final @NotNull WarDirectory warDirectory;
  private final @NotNull String warRelativePath;
  private final @NotNull File file;

  @PropertyMapping({"warDirectory", "warRelativePath", "file"})
  public WebResource(@NotNull WarDirectory warDirectory, @NotNull String warRelativePath, @NotNull File file) {
    this.warDirectory = warDirectory;
    this.warRelativePath = getAdjustedPath(warRelativePath);
    this.file = file;
  }

  public @NotNull WarDirectory getWarDirectory() {
    return warDirectory;
  }

  public @NotNull String getWarRelativePath() {
    return warRelativePath;
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
    if (!(o instanceof WebResource resource)) return false;

    if (!file.getPath().equals(resource.file.getPath())) return false;
    if (warDirectory != resource.warDirectory) return false;
    if (!warRelativePath.equals(resource.warRelativePath)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = warDirectory.hashCode();
    result = 31 * result + warRelativePath.hashCode();
    result = 31 * result + file.getPath().hashCode();
    return result;
  }

  @Override
  public String toString() {
    return "WebResource{" +
           "myWarDirectory=" + warDirectory +
           ", warRelativePath='" + warRelativePath + '\'' +
           ", file=" + file +
           '}';
  }
}
