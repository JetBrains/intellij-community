// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.openapi.vcs;


import org.jetbrains.annotations.NotNull;

public class DefaultRepositoryLocation implements RepositoryLocation {
  private final String myURL;
  private final String myLocation;

  public DefaultRepositoryLocation(@NotNull String URL) {
    this(URL, URL);
  }

  public DefaultRepositoryLocation(@NotNull String URL, final String location) {
    myURL = URL;
    myLocation = location;
  }

  public @NotNull String getURL() {
    return myURL;
  }

  @Override
  public String toString() {
    return myLocation;
  }

  @Override
  public @NotNull String toPresentableString() {
    return myURL;
  }

  @Override
  public String getKey() {
    return myURL + "|" + myLocation;
  }

  public String getLocation() {
    return myLocation;
  }
}
