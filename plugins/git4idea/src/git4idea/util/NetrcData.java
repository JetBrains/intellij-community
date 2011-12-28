/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package git4idea.util;

import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.SystemProperties;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.StringTokenizer;

/**
 * Parses {@code .netrc} file and stores the parsed authentication information in {@link AuthRecord} objects.
 * Parse only {@code machine}, {@code login} and {@code password} fields.
 *
 * @author Kirill Likhodedov
 */
public class NetrcData {

  private static final String NETRC_FILE = SystemInfo.isWindows ? "_netrc" : ".netrc";
  private final Map<String, AuthRecord> myAuthDataMap;

  private NetrcData(@NotNull Map<String, AuthRecord> authData) {
    myAuthDataMap = authData;
  }

  /**
   * Parses the {@code .netrc} file (which is {@code _netrc} on Windows) and returns the NetrcData object which can be analyzed further.
   */
  @NotNull
  public static NetrcData parse() throws IOException {
    File netrc = getNetrcFile();
    return parse(netrc);
  }

  @NotNull
  private static File getNetrcFile() {
    String home = System.getenv("HOME");
    if (home == null || !new File(home, NETRC_FILE).exists()) {
      home = SystemProperties.getUserHome();
    }
    return new File(home, NETRC_FILE);
  }

  @NotNull
  static NetrcData parse(@NotNull File netrcFile) throws IOException {
    if (!netrcFile.exists()) {
      throw new FileNotFoundException(netrcFile.getPath());
    }
    String netrcContent = FileUtil.loadFile(netrcFile);
    return parse(netrcContent);
  }

  /**
   * @return true iff the host of the given url is contained in {@code .netrc}, and both login and password are set for this host.
   */
  public boolean hasAuthDataForUrl(@NotNull String url) {
    String host = getHostFromUrl(url);
    AuthRecord authRecord = myAuthDataMap.get(host);
    return authRecord != null && authRecord.getLogin() != null && authRecord.getPassword() != null;
  }

  @NotNull
  Collection<AuthRecord> getAuthData() {
    return myAuthDataMap.values();
  }
    
  @NotNull
  private static String getHostFromUrl(@NotNull String url) {
    final String schemaSeparator = "://";
    String urlWithoutSchema = url.substring(url.indexOf(schemaSeparator) + schemaSeparator.length());
    return urlWithoutSchema.substring(0, urlWithoutSchema.indexOf('/'));
  }

  @NotNull
  private static NetrcData parse(@NotNull String content) {
    Map<String, AuthRecord> result = new HashMap<String, AuthRecord>();
    StringTokenizer tokenizer = new StringTokenizer(content);
    AuthRecord currentRecord = null;
    while (tokenizer.hasMoreTokens()) {
      String token = tokenizer.nextToken();
      if (token.equalsIgnoreCase("machine")) {
        if (!tokenizer.hasMoreTokens()) {
          break;
        }
        String host = tokenizer.nextToken();
        host = host.toLowerCase(); // hosts are case insensitive
        if (result.containsKey(host)) {
          currentRecord = result.get(host);
        } else {
          currentRecord = new AuthRecord(host);
          result.put(host, currentRecord);
        }
      }
      else if (token.equalsIgnoreCase("login")) {
        if (!tokenizer.hasMoreTokens()) {
          break;
        }
        if (currentRecord != null && currentRecord.getLogin() == null) {
          currentRecord.setLogin(tokenizer.nextToken());
        }
      }
      else if (token.equalsIgnoreCase("password")) {
        if (!tokenizer.hasMoreTokens()) {
          break;
        }
        if (currentRecord != null && currentRecord.getPassword() == null) {
          currentRecord.setPassword(tokenizer.nextToken());
        }
      }
    }
    return new NetrcData(result);
  }

  static class AuthRecord {
    private final String myHost;
    private String myLogin;
    private String myPassword;

    AuthRecord(String host, String login, String password) {
      myHost = host;
      myLogin = login;
      myPassword = password;
    }

    private AuthRecord(String host) {
      myHost = host;
    }

    @Nullable
    String getLogin() {
      return myLogin;
    }

    @Nullable
    String getPassword() {
      return myPassword;
    }

    private void setLogin(String login) {
      myLogin = login;
    }

    private void setPassword(String password) {
      myPassword = password;
    }

    @Override
    public String toString() {
      return String.format("AuthRecord{host=%s, login=%s, password=%s}", myHost, myLogin, myPassword);
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      AuthRecord record = (AuthRecord)o;

      if (myHost != null ? !myHost.equalsIgnoreCase(record.myHost) : record.myHost != null) return false;  // NB: host is case insensitive
      if (myLogin != null ? !myLogin.equals(record.myLogin) : record.myLogin != null) return false;
      if (myPassword != null ? !myPassword.equals(record.myPassword) : record.myPassword != null) return false;

      return true;
    }

    @Override
    public int hashCode() {
      int result = myHost != null ? myHost.hashCode() : 0;
      result = 31 * result + (myLogin != null ? myLogin.hashCode() : 0);
      result = 31 * result + (myPassword != null ? myPassword.hashCode() : 0);
      return result;
    }
  }

}
