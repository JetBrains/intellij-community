// Copyright 2008-2010 Victor Iacoban
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software distributed under
// the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
// either express or implied. See the License for the specific language governing permissions and
// limitations under the License.
package org.zmlx.hg4idea.provider;

import com.intellij.openapi.vcs.RepositoryLocation;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.VirtualFile;

public class HgRepositoryLocation implements RepositoryLocation {

  private final String myUrl;
  private final VirtualFile myRoot;

  public HgRepositoryLocation(String url, VirtualFile root) {
    myUrl = url;
    myRoot = root;
  }

  public VirtualFile getRoot() {
    return myRoot;
  }

  public String toPresentableString() {
    return myUrl;
  }

  @Override
  public String toString() {
    return toPresentableString();
  }

  public String getKey() {
    return myUrl;
  }

  @Override
  public void onBeforeBatch() {
  }

  @Override
  public void onAfterBatch() {
  }
}
