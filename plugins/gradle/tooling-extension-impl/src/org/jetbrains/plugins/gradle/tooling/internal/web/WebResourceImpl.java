// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.tooling.internal.web;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.model.web.WebConfiguration;

import java.io.File;

/**
 * @author Vladislav.Soroka
 */
public class WebResourceImpl implements WebConfiguration.WebResource {
  private final @NotNull String myWarDirectory;
  private final @NotNull String myRelativePath;
  private final @NotNull File file;

  public WebResourceImpl(@NotNull String warDirectory, @NotNull String relativePath, @NotNull File file) {
    myWarDirectory = warDirectory;
    this.myRelativePath = relativePath;
    this.file = file;
  }

  @Override
  public @NotNull String getWarDirectory() {
    return myWarDirectory;
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
    if (!(o instanceof WebResourceImpl)) return false;

    WebResourceImpl resource = (WebResourceImpl)o;
    if (!file.getPath().equals(resource.file.getPath())) return false;
    if (!myWarDirectory.equals(resource.myWarDirectory)) return false;
    if (!myRelativePath.equals(resource.myRelativePath)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = myWarDirectory.hashCode();
    result = 31 * result + myRelativePath.hashCode();
    result = 31 * result + file.getPath().hashCode();
    return result;
  }

  @Override
  public String toString() {
    return "WebResourceImpl{" +
           "myWarDirectory=" + myWarDirectory +
           ", warRelativePath='" + myRelativePath + '\'' +
           ", file=" + file +
           '}';
  }
}
