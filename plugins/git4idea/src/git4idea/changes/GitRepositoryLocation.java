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

  public File getRoot() {
    return myRoot;
  }
}
