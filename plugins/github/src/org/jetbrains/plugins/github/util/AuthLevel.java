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

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface AuthLevel {
  boolean accepts(@NotNull GithubAuthData auth);

  @Nullable
  default String getHost() {
    return null;
  }

  @Nullable
  default GithubAuthData.AuthType getAuthType() {
    return null;
  }

  default boolean isOnetime() {
    return false;
  }


  AuthLevel ANY = new AuthLevelImpl(null, null);
  AuthLevel TOKEN = new AuthLevelImpl(null, GithubAuthData.AuthType.TOKEN);
  AuthLevel BASIC = new AuthLevelImpl(null, GithubAuthData.AuthType.BASIC);

  AuthLevel LOGGED = new AuthLevel() {
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
  static AuthLevel basicOnetime(@NotNull String host) {
    return new AuthLevelImpl(host, GithubAuthData.AuthType.BASIC) {
      @Override
      public boolean isOnetime() {
        return true;
      }
    };
  }


  class AuthLevelImpl implements AuthLevel {
    @Nullable private final String myHost;
    @Nullable private final GithubAuthData.AuthType myAuthType;

    public AuthLevelImpl(@Nullable String host, @Nullable GithubAuthData.AuthType authType) {
      myHost = host;
      myAuthType = authType;
    }

    @Nullable
    @Override
    public String getHost() {
      return myHost;
    }

    @Nullable
    @Override
    public GithubAuthData.AuthType getAuthType() {
      return myAuthType;
    }

    @Override
    public boolean accepts(@NotNull GithubAuthData auth) {
      if (myHost != null && !myHost.equals(auth.getHost())) return false;
      if (myAuthType != null && !myAuthType.equals(auth.getAuthType())) return false;
      return true;
    }

    @Override
    public String toString() {
      String s = "";
      if (myAuthType != null) s += myAuthType.name();
      if (myHost != null) {
        if (!s.isEmpty()) s += " ";
        s += "for " + myHost;
      }
      return s;
    }
  }
}
