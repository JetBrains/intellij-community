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
package org.jetbrains.plugins.github.util;

import org.jetbrains.annotations.NotNull;

/**
 * @deprecated {@link org.jetbrains.plugins.github.authentication.GithubAuthenticationManager}
 */
@Deprecated
public class GithubAuthDataHolder {
  @NotNull private GithubAuthData myAuthData;

  public GithubAuthDataHolder(@NotNull GithubAuthData auth) {
    myAuthData = auth;
  }

  @NotNull
  public synchronized GithubAuthData getAuthData() {
    return myAuthData;
  }
}
