// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package git4idea.changes;

import com.intellij.openapi.vcs.RepositoryLocation;

import java.io.File;

public class GitRepositoryLocation implements RepositoryLocation {
  private final String myUrl; // repository url
  private final File myRoot; // repository root

  public GitRepositoryLocation(String url, File root) {
    myUrl = url;
    myRoot = root;
  }

  @Override
  public String toPresentableString() {
    return myUrl;
  }

  @Override
  public String toString() {
    return toPresentableString();
  }

  @Override
  public String getKey() {
    return myUrl;
  }

  @Override
  public void onBeforeBatch() {
  }

  @Override
  public void onAfterBatch() {
  }

  public File getRoot() {
    return myRoot;
  }
}
