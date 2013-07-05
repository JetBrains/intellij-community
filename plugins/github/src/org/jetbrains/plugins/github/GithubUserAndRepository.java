/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package org.jetbrains.plugins.github;

import org.jetbrains.annotations.NotNull;

/**
* @author Aleksey Pivovarov
*/
public class GithubUserAndRepository {
  @NotNull final private String myUserName;
  @NotNull final private String myRepositoryName;

  public GithubUserAndRepository(@NotNull String userName, @NotNull String repositoryName) {
    myUserName = userName;
    myRepositoryName = repositoryName;
  }

  @NotNull
  public String getUserName() {
    return myUserName;
  }

  @NotNull
  public String getRepositoryName() {
    return myRepositoryName;
  }

  @NotNull
  public String toString() {
    return myUserName + '/' + myRepositoryName;
  }
}
