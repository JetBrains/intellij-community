// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.model.data;

import com.intellij.serialization.PropertyMapping;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.Serializable;
import java.nio.file.Path;
import java.util.Objects;

/**
 * @author Vladislav.Soroka
 */
@SuppressWarnings("IO_FILE_USAGE")
public class WebResource implements Serializable {
  private static final long serialVersionUID = 1L;

  private final @NotNull WarDirectory warDirectory;
  private final @NotNull String warRelativePath;
  private final @NotNull Path filePath;

  /**
   * @deprecated use {@link #WebResource(WarDirectory, String, Path)} instead.
   */
  @Deprecated
  @PropertyMapping({"warDirectory", "warRelativePath", "file"})
  public WebResource(@NotNull WarDirectory warDirectory, @NotNull String warRelativePath, @NotNull File file) {
    this(warDirectory, warRelativePath, file.toPath());
  }

  @PropertyMapping({"warDirectory", "warRelativePath", "file"})
  public WebResource(@NotNull WarDirectory warDirectory, @NotNull String warRelativePath, @NotNull Path filePath) {
    this.warDirectory = warDirectory;
    this.warRelativePath = getAdjustedPath(warRelativePath);
    this.filePath = filePath;
  }

  public @NotNull WarDirectory getWarDirectory() {
    return warDirectory;
  }

  public @NotNull String getWarRelativePath() {
    return warRelativePath;
  }

  /**
   * @deprecated use {@link #getFilePath()} instead.
   */
  @Deprecated
  public @NotNull File getFile() {
    return filePath.toFile();
  }

  public @NotNull Path getFilePath() {
    return filePath;
  }

  private static String getAdjustedPath(final @NotNull String path) {
    return path.isEmpty() || path.charAt(0) != '/' ? '/' + path : path;
  }

  @Override
  public boolean equals(Object o) {
    if (o == null || getClass() != o.getClass()) return false;
    WebResource resource = (WebResource)o;
    return Objects.equals(warDirectory, resource.warDirectory) &&
           Objects.equals(warRelativePath, resource.warRelativePath) &&
           Objects.equals(filePath, resource.filePath);
  }

  @Override
  public int hashCode() {
    return Objects.hash(warDirectory, warRelativePath, filePath);
  }

  @Override
  public String toString() {
    return "WebResource{" +
           "warDirectory=" + warDirectory +
           ", warRelativePath='" + warRelativePath + '\'' +
           ", filePath=" + filePath +
           '}';
  }
}
