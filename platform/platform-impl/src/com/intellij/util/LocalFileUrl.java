package com.intellij.util;

import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class LocalFileUrl implements Url {
  private final String path;

  /**
   * Use {@link Urls#newLocalFileUrl(String)} instead
   */
  public LocalFileUrl(@NotNull String path) {
    this.path = path;
  }

  @NotNull
  @Override
  public String getPath() {
    return path;
  }

  @Override
  public boolean isInLocalFileSystem() {
    return true;
  }

  @Override
  public String toDecodedForm() {
    return path;
  }

  @NotNull
  @Override
  public String toExternalForm() {
    return path;
  }

  @Nullable
  @Override
  public String getScheme() {
    return null;
  }

  @Nullable
  @Override
  public String getAuthority() {
    return null;
  }

  @Nullable
  @Override
  public String getParameters() {
    return null;
  }

  @NotNull
  @Override
  public Url trimParameters() {
    return this;
  }

  @Override
  public String toString() {
    return toExternalForm();
  }

  @Override
  public boolean equals(Object o) {
    return this == o || ((o instanceof LocalFileUrl) && path.equals(((LocalFileUrl)o).path));
  }

  @Override
  public boolean equalsIgnoreCase(@Nullable Url o) {
    return this == o || ((o instanceof LocalFileUrl) && path.equalsIgnoreCase(((LocalFileUrl)o).path));
  }

  @Override
  public boolean equalsIgnoreParameters(@Nullable Url url) {
    return equals(url);
  }

  @Override
  public int hashCode() {
    return path.hashCode();
  }

  @Override
  public int hashCodeCaseInsensitive() {
    return StringUtil.stringHashCodeInsensitive(path);
  }
}