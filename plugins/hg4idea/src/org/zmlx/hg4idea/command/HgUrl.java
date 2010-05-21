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
package org.zmlx.hg4idea.command;

import org.apache.commons.lang.StringUtils;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Map;

public class HgUrl {

  private static final Map<String, HgUrlScheme> SCHEMES = new HashMap<String, HgUrlScheme>();

  private URI uri;
  private HgUrlScheme scheme;

  private String username;
  private String password;

  static {
    SCHEMES.put("http", HgUrlScheme.HTTP);
    SCHEMES.put("https", HgUrlScheme.HTTPS);
    SCHEMES.put("ssh", HgUrlScheme.SSH);
    SCHEMES.put("file", HgUrlScheme.FILE);
    SCHEMES.put("", HgUrlScheme.FILE);
    SCHEMES.put(null, HgUrlScheme.FILE);
  }

  public HgUrl(String urlString) throws URISyntaxException {
    uri = new URI(urlString).normalize();
    scheme = SCHEMES.get(uri.getScheme());
  }

  public boolean supportsAuthentication() {
    return scheme.supportsAuthentication;
  }

  public String getUsername() {
    if (username == null) {
      username = findUserInfoPart(0);
    }
    return username;
  }

  public String getPassword() {
    if (password == null) {
      password = findUserInfoPart(1);
    }
    return password;
  }

  public void setUsername(String username) {
    this.username = username;
  }

  public void setPassword(String password) {
    this.password = password;
  }

  public String asString() throws URISyntaxException {
    return new URI(
      uri.getScheme(),
      username + ":" + password,
      uri.getHost(),
      uri.getPort(),
      uri.getPath(),
      uri.getQuery(),
      uri.getFragment()
    ).toString();
  }

  private String findUserInfoPart(int index) {
    String userInfo = uri.getUserInfo();
    if (StringUtils.isBlank(userInfo)) {
      return null;
    }

    String[] parts = StringUtils.splitPreserveAllTokens(userInfo, ':');
    if (parts.length > index && StringUtils.isNotBlank(parts[index])) {
      return  parts[index];
    }
    return null;
  }

  enum HgUrlScheme {
    HTTP(true),
    HTTPS(true),
    SSH(true),
    FILE(false);

    private final boolean supportsAuthentication;

    HgUrlScheme(boolean supportsAuthentication) {
      this.supportsAuthentication = supportsAuthentication;
    }
  }
}


