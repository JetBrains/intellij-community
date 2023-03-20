// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.api;

import com.intellij.collaboration.api.ServerPath;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.io.URLUtil;
import com.intellij.util.xmlb.annotations.Attribute;
import com.intellij.util.xmlb.annotations.Tag;
import git4idea.remote.hosting.GitHostingUrlUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.github.exceptions.GithubParseException;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Github server reference allowing to specify custom port and path to instance
 */
@Tag("server")
public final class GithubServerPath implements ServerPath {
  public static final String DEFAULT_HOST = "github.com";
  public static final GithubServerPath DEFAULT_SERVER = new GithubServerPath(DEFAULT_HOST);
  private static final String API_PREFIX = "api.";
  private static final String API_SUFFIX = "/api";
  private static final String ENTERPRISE_API_V3_SUFFIX = "/v3";
  private static final String GRAPHQL_SUFFIX = "/graphql";

  @Attribute("useHttp")
  @Nullable private final Boolean myUseHttp;
  @Attribute("host")
  @NotNull private final String myHost;
  @Attribute("port")
  @Nullable private final Integer myPort;
  @Attribute("suffix")
  @Nullable private final String mySuffix;

  public GithubServerPath() {
    this(null, "", null, null);
  }

  public GithubServerPath(@NonNls @NotNull String host) {
    this(null, host, null, null);
  }

  public GithubServerPath(@Nullable Boolean useHttp,
                          @NonNls @NotNull String host,
                          @Nullable Integer port,
                          @NonNls @Nullable String suffix) {
    myUseHttp = useHttp;
    myHost = StringUtil.toLowerCase(host);
    myPort = port;
    mySuffix = suffix;
  }

  @NotNull
  public String getSchema() {
    return (myUseHttp == null || !myUseHttp) ? "https" : "http";
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

  /**
   * @deprecated use an util method directly
   */
  @Deprecated
  public boolean matches(@NotNull String gitRemoteUrl) {
    return GitHostingUrlUtil.match(toURI(), gitRemoteUrl);
  }

  // 1 - schema, 2 - host, 4 - port, 5 - path
  private final static Pattern URL_REGEX = Pattern.compile("^(https?://)?([^/?:]+)(:(\\d+))?((/[^/?#]+)*)?/?",
                                                           Pattern.CASE_INSENSITIVE);

  @NotNull
  public static GithubServerPath from(@NotNull String uri) throws GithubParseException {
    Matcher matcher = URL_REGEX.matcher(uri);

    if (!matcher.matches()) throw new GithubParseException("Not a valid URL");
    String schema = matcher.group(1);
    Boolean httpSchema = (schema == null || schema.isEmpty()) ? null : schema.equalsIgnoreCase("http://");
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

    return new GithubServerPath(httpSchema, host, port, path);
  }

  @NotNull
  public String toUrl() {
    return toUrl(true);
  }

  @NotNull
  public String toUrl(boolean showSchema) {
    StringBuilder builder = new StringBuilder();
    if (showSchema) builder.append(getSchemaUrlPart());
    builder.append(myHost).append(getPortUrlPart()).append(StringUtil.notNullize(mySuffix));
    return builder.toString();
  }

  @NotNull
  public String toApiUrl() {
    StringBuilder builder = new StringBuilder(getSchemaUrlPart());
    if (isGithubDotCom()) {
      builder.append(API_PREFIX).append(myHost).append(getPortUrlPart()).append(StringUtil.notNullize(mySuffix));
    }
    else {
      builder.append(myHost).append(getPortUrlPart()).append(StringUtil.notNullize(mySuffix)).append(API_SUFFIX)
        .append(ENTERPRISE_API_V3_SUFFIX);
    }
    return builder.toString();
  }

  @NotNull
  public String toGraphQLUrl() {
    StringBuilder builder = new StringBuilder(getSchemaUrlPart());
    if (isGithubDotCom()) {
      builder.append(API_PREFIX).append(myHost).append(getPortUrlPart()).append(StringUtil.notNullize(mySuffix)).append(GRAPHQL_SUFFIX);
    }
    else {
      builder.append(myHost).append(getPortUrlPart()).append(StringUtil.notNullize(mySuffix)).append(API_SUFFIX).append(GRAPHQL_SUFFIX);
    }
    return builder.toString();
  }

  @Override
  public @NotNull URI toURI() {
    int port = getPort() == null ? -1 : getPort();
    try {
      return new URI(getSchema(), null, getHost(), port, getSuffix(), null, null);
    }
    catch (URISyntaxException e) {
      // shouldn't happen, because we pre-validate the data
      throw new RuntimeException(e);
    }
  }

  public boolean isGithubDotCom() {
    return myHost.equalsIgnoreCase(DEFAULT_HOST);
  }

  public @NlsSafe @NotNull String toString() {
    String schema = myUseHttp != null ? getSchemaUrlPart() : "";
    return schema + myHost + getPortUrlPart() + StringUtil.notNullize(mySuffix);
  }

  @NotNull
  private String getPortUrlPart() {
    return myPort != null ? (":" + myPort.toString()) : "";
  }

  @NotNull
  private String getSchemaUrlPart() {
    return getSchema() + URLUtil.SCHEME_SEPARATOR;
  }

  @SuppressWarnings("EqualsWhichDoesntCheckParameterClass")
  @Override
  public boolean equals(Object o) {
    return equals(o, false);
  }

  public boolean equals(Object o, boolean ignoreProtocol) {
    if (this == o) return true;
    if (!(o instanceof GithubServerPath path)) return false;
    return (ignoreProtocol || Objects.equals(myUseHttp, path.myUseHttp)) &&
           Objects.equals(myHost, path.myHost) &&
           Objects.equals(myPort, path.myPort) &&
           Objects.equals(mySuffix, path.mySuffix);
  }

  @Override
  public int hashCode() {
    return Objects.hash(myHost, myPort, mySuffix);
  }
}