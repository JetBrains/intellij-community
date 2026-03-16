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
public class EarResource implements Serializable {
  private static final long serialVersionUID = 1L;

  private final @NotNull String earDirectory;
  private final @NotNull String relativePath;
  private final @NotNull Path filePath;

  /**
   * @deprecated use {@link #EarResource(String, String, Path)} instead.
   */
  @Deprecated
  @PropertyMapping({"earDirectory", "relativePath", "file"})
  public EarResource(@NotNull String earDirectory, @NotNull String relativePath, @NotNull File file) {
    this(earDirectory, relativePath, file.toPath());
  }

  @PropertyMapping({"earDirectory", "relativePath", "file"})
  public EarResource(@NotNull String earDirectory, @NotNull String relativePath, @NotNull Path filePath) {
    this.earDirectory = earDirectory;
    this.relativePath = getAdjustedPath(relativePath);
    this.filePath = filePath;
  }

  public @NotNull String getEarDirectory() {
    return earDirectory;
  }

  public @NotNull String getRelativePath() {
    return relativePath;
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
    EarResource resource = (EarResource)o;
    return Objects.equals(earDirectory, resource.earDirectory) &&
           Objects.equals(relativePath, resource.relativePath) &&
           Objects.equals(filePath, resource.filePath);
  }

  @Override
  public int hashCode() {
    return Objects.hash(earDirectory, relativePath, filePath);
  }

  @Override
  public String toString() {
    return "EarResource{" +
           "earDirectory='" + earDirectory + '\'' +
           ", relativePath='" + relativePath + '\'' +
           ", filePath=" + filePath +
           '}';
  }
}
