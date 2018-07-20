/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

import com.google.common.base.MoreObjects;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @deprecated {@link org.jetbrains.plugins.github.authentication.GithubAuthenticationManager}
 */
@Deprecated
public class AuthLevel {
  public static final AuthLevel ANY = new AuthLevel(null, null);
  public static final AuthLevel TOKEN = new AuthLevel(null, GithubAuthData.AuthType.TOKEN);
  public static final AuthLevel BASIC = new AuthLevel(null, GithubAuthData.AuthType.BASIC);

  public static final AuthLevel LOGGED = new AuthLevel(null, null) {
    @Override
    public boolean accepts(@NotNull GithubAuthData auth) {
      return auth.getAuthType() != GithubAuthData.AuthType.ANONYMOUS;
    }

    @Override
    public String toString() {
      return "Not anonymous";
    }
  };

  @NotNull
  public static AuthLevel basicOnetime(@NotNull String host) {
    return new AuthLevel(host, GithubAuthData.AuthType.BASIC) {
      @Override
      public boolean isOnetime() {
        return true;
      }
    };
  }


  @Nullable private final String myHost;
  @Nullable private final GithubAuthData.AuthType myAuthType;

  private AuthLevel(@Nullable String host, @Nullable GithubAuthData.AuthType authType) {
    myHost = host;
    myAuthType = authType;
  }

  @Nullable
  public String getHost() {
    return myHost;
  }

  @Nullable
  public GithubAuthData.AuthType getAuthType() {
    return myAuthType;
  }

  public boolean accepts(@NotNull GithubAuthData auth) {
    if (myHost != null && !myHost.equals(auth.getHost())) return false;
    if (myAuthType != null && !myAuthType.equals(auth.getAuthType())) return false;
    return true;
  }

  public boolean isOnetime() {
    return false;
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this).add("authType", myAuthType).add("host", myHost).toString();
  }
}
