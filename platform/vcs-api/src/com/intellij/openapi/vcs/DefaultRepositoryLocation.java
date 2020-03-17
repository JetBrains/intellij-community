// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.openapi.vcs;

/**
 * @author yole
 */
public class DefaultRepositoryLocation implements RepositoryLocation {
  private final String myURL;
  private final String myLocation;

  public DefaultRepositoryLocation(final String URL) {
    this(URL, URL);
  }

  public DefaultRepositoryLocation(final String URL, final String location) {
    myURL = URL;
    myLocation = location;
  }

  public String getURL() {
    return myURL;
  }

  public String toString() {
    return myLocation;
  }

  @Override
  public String toPresentableString() {
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
