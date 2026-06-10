// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.model.data;

import com.intellij.serialization.PropertyMapping;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.Serializable;
import java.nio.file.Path;

/**
 * @author Vladislav.Soroka
 */
@SuppressWarnings("IO_FILE_USAGE")
public class Jar implements Serializable {
  private static final long serialVersionUID = 1L;

  private final @NotNull String name;
  private @Nullable Path archivePath;

  private @Nullable String manifestContent;

  @PropertyMapping({"name"})
  public Jar(@NotNull String name) {
    this.name = name;
  }

  public @NotNull String getName() {
    return name;
  }

  public @Nullable String getManifestContent() {
    return manifestContent;
  }

  public void setManifestContent(@Nullable String manifestContent) {
    this.manifestContent = manifestContent;
  }

  /**
   * @deprecated Use {@link getJarArchivePath()} instead.
   */
  @Deprecated
  public @Nullable File getArchivePath() {
    return archivePath == null ? null : archivePath.toFile();
  }

  /**
   * @deprecated Use {@link setArchivePath(Path)} instead.
   */
  @Deprecated
  public void setArchivePath(@Nullable File archivePath) {
    this.archivePath = archivePath == null ? null : archivePath.toPath();
  }

  public void setArchivePath(@Nullable Path archivePath) {
    this.archivePath = archivePath;
  }

  public @Nullable Path getJarArchivePath() {
    return archivePath;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    if (!super.equals(o)) return false;
    Jar that = (Jar)o;
    if (!name.equals(that.name)) return false;
    return true;
  }

  @Override
  public int hashCode() {
    int result = super.hashCode();
    result = 31 * result + name.hashCode();
    return result;
  }

  @Override
  public String toString() {
    return "Jar{'" + name + "'}";
  }
}
