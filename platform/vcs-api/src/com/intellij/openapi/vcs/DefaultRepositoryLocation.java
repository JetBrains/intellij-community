/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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

  public String toPresentableString() {
    return myURL;
  }

  public String getKey() {
    return myURL + "|" + myLocation;
  }

  @Override
  public void onBeforeBatch() throws VcsException {
  }

  @Override
  public void onAfterBatch() {
  }

  public String getLocation() {
    return myLocation;
  }
}
