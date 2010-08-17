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
import com.intellij.openapi.vcs.VcsException;

import java.io.File;

/**
 * The git repository location
 */
public class GitRepositoryLocation implements RepositoryLocation {
  /**
   * The URL of the repository
   */
  private final String myUrl;
  /**
   * The repository root
   */
  private final File myRoot;

  /**
   * A constructor
   *
   * @param url  an URL of the repository
   * @param root the vcs root
   */
  public GitRepositoryLocation(String url, File root) {
    myUrl = url;
    myRoot = root;
  }


  /**
   * {@inheritDoc}
   */
  public String toPresentableString() {
    return myUrl;
  }

  /**
   * {@inheritDoc}
   */
  public String getKey() {
    return myUrl;
  }

  public void onBeforeBatch() throws VcsException {
  }

  public void onAfterBatch() {
  }

  /**
   * @return vcs root for the repository
   */
  public File getRoot() {
    return myRoot;
  }
}
