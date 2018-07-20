// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.api;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.xmlb.annotations.Attribute;
import com.intellij.util.xmlb.annotations.Tag;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.github.exceptions.GithubParseException;
import org.jetbrains.plugins.github.util.GithubUrlUtil;

import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import java.util.Locale;

/**
 * Github server reference allowing to specify custom port and path to instance
 */
@Tag("server")
public class GithubServerPath {
  @Attribute("host")
  @NotNull private final String myHost;
  @Attribute("port")
  @Nullable private final Integer myPort;
  @Attribute("suffix")
  @Nullable private final String mySuffix;

  public GithubServerPath() {
    this("", null, null);
  }

  public GithubServerPath(@NonNls @NotNull String host) {
    this(host, null, null);
  }

  public GithubServerPath(@NonNls @NotNull String host, @Nullable Integer port, @NonNls @Nullable String suffix) {
    myHost = host.toLowerCase(Locale.ENGLISH);
    myPort = port;
    mySuffix = suffix != null ? suffix.toLowerCase(Locale.ENGLISH) : null;
  }

  @NotNull
  public String getHost() {
    return myHost;
  }

  @Nullable
  public Integer getPort() {
    return myPort;
  }

  @Nullable
  public String getSuffix() {
    return mySuffix;
  }

  public boolean matches(@NotNull String gitRemoteUrl) {
    String url = GithubUrlUtil.removePort(GithubUrlUtil.removeProtocolPrefix(gitRemoteUrl));
    return StringUtil.startsWithIgnoreCase(url, myHost + StringUtil.notNullize(mySuffix));
  }

  // 2 - host, 4 - port, 5 - path
  private final static Pattern URL_REGEX = Pattern.compile("^(https?://)?([^/?:]+)(:(\\d+))?((/[^/?#]+)*)?");

  @NotNull
  public static GithubServerPath from(@NotNull String uri) throws GithubParseException {
    Matcher matcher = URL_REGEX.matcher(uri);

    if (!matcher.matches()) throw new GithubParseException("Not a valid URL");
    String host = matcher.group(2);
    if (host == null) throw new GithubParseException("Empty host");

    Integer port;
    String portGroup = matcher.group(4);
    if (portGroup == null) {
      port = null;
    }
    else {
      try {
        port = Integer.parseInt(portGroup);
      }
      catch (NumberFormatException e) {
        throw new GithubParseException("Invalid port format");
      }
    }

    String path = StringUtil.nullize(matcher.group(5));

    return new GithubServerPath(host, port, path);
  }

  public String toString() {
    String port = myPort != null ? (":" + myPort.toString()) : "";
    return myHost + port + StringUtil.notNullize(mySuffix);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof GithubServerPath)) return false;
    GithubServerPath path = (GithubServerPath)o;
    return Objects.equals(myHost, path.myHost) &&
           Objects.equals(myPort, path.myPort) &&
           Objects.equals(mySuffix, path.mySuffix);
  }

  @Override
  public int hashCode() {

    return Objects.hash(myHost, myPort, mySuffix);
  }
}
