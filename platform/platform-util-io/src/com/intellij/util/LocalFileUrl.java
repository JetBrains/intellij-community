// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util;

import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

public final class LocalFileUrl implements Url {
  private final String path;

  /** Use {@link Urls#newLocalFileUrl(String)} instead */
  public LocalFileUrl(@NotNull String path) {
    this.path = path;
  }

  @Override
  public @NotNull Url resolve(@NotNull String subPath) {
    return new LocalFileUrl(path.isEmpty() ? subPath : (path + "/" + subPath));
  }

  @Override
  public @NotNull Url addParameters(@NotNull Map<String, String> parameters) {
    throw new UnsupportedOperationException("File URL doesn't support parameters");
  }

  @Override
  public @NotNull String getPath() {
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

  @Override
  public @NotNull String toExternalForm() {
    return path;
  }

  @Override
  public @Nullable String getScheme() {
    return null;
  }

  @Override
  public @Nullable String getAuthority() {
    return null;
  }

  @Override
  public @Nullable String getParameters() {
    return null;
  }

  @Override
  public @NotNull Url trimParameters() {
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
