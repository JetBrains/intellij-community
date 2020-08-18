// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.util;

import com.intellij.util.UriUtil;
import com.intellij.util.io.URLUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.github.api.GHRepositoryPath;
import org.jetbrains.plugins.github.api.GithubServerPath;

import java.net.URI;
import java.net.URISyntaxException;

/**
 * @author Aleksey Pivovarov
 */
public class GithubUrlUtil {
  @NotNull
  public static String removeProtocolPrefix(String url) {
    int index = url.indexOf('@');
    if (index != -1) {
      return url.substring(index + 1).replace(':', '/');
    }
    index = url.indexOf("://");
    if (index != -1) {
      return url.substring(index + 3);
    }
    return url;
  }

  /**
   * Will only work correctly after {@link #removeProtocolPrefix(String)}
   */
  @NotNull
  public static String removePort(@NotNull String url) {
    int index = url.indexOf(':');
    if (index == -1) return url;
    int slashIndex = url.indexOf('/');
    if (slashIndex != -1 && slashIndex < index) return url;

    String beforePort = url.substring(0, index);
    if (slashIndex == -1) {
      return beforePort;
    }
    else {
      return beforePort + url.substring(slashIndex);
    }
  }

  @NotNull
  public static String removeTrailingSlash(@NotNull String s) {
    if (s.endsWith("/")) {
      return s.substring(0, s.length() - 1);
    }
    return s;
  }

  @Deprecated
  @NotNull
  public static String getApiUrl(@NotNull String urlFromSettings) {
    return GithubServerPath.from(urlFromSettings).toApiUrl();
  }

  /**
   * assumed isGithubUrl(remoteUrl)
   * <p>
   * git@github.com:user/repo.git -> user/repo
   */
  @Nullable
  public static GHRepositoryPath getUserAndRepositoryFromRemoteUrl(@NotNull String remoteUrl) {
    remoteUrl = removeProtocolPrefix(removeEndingDotGit(remoteUrl));
    int index1 = remoteUrl.lastIndexOf('/');
    if (index1 == -1) {
      return null;
    }
    String url = remoteUrl.substring(0, index1);
    int index2 = Math.max(url.lastIndexOf('/'), url.lastIndexOf(':'));
    if (index2 == -1) {
      return null;
    }
    final String username = remoteUrl.substring(index2 + 1, index1);
    final String reponame = remoteUrl.substring(index1 + 1);
    if (username.isEmpty() || reponame.isEmpty()) {
      return null;
    }
    return new GHRepositoryPath(username, reponame);
  }

  @NotNull
  private static String removeEndingDotGit(@NotNull String url) {
    url = removeTrailingSlash(url);
    final String DOT_GIT = ".git";
    if (url.endsWith(DOT_GIT)) {
      return url.substring(0, url.length() - DOT_GIT.length());
    }
    return url;
  }

  @Nullable
  public static URI getUriFromRemoteUrl(@NotNull String remoteUrl) {
    String fixed = removeEndingDotGit(UriUtil.trimTrailingSlashes(remoteUrl));
    try {
      if (!fixed.contains(URLUtil.SCHEME_SEPARATOR)) {
        //scp-style
        return new URI(URLUtil.HTTPS_PROTOCOL + URLUtil.SCHEME_SEPARATOR + removeProtocolPrefix(fixed).replace(':', '/'));
      }
      return new URI(fixed);
    }
    catch (URISyntaxException e) {
      return null;
    }
  }

  //region Deprecated

  /**
   * E.g.: https://github.com/suffix/ -> github.com
   * github.com:8080/ -> github.com
   *
   * @deprecated {@link org.jetbrains.plugins.github.api.GHRepositoryCoordinates}
   */
  @Deprecated
  @NotNull
  public static String getHostFromUrl(@NotNull String url) {
    String path = removeProtocolPrefix(url).replace(':', '/');
    int index = path.indexOf('/');
    if (index == -1) {
      return path;
    }
    else {
      return path.substring(0, index);
    }
  }

  /**
   * @deprecated {@link org.jetbrains.plugins.github.api.GHRepositoryCoordinates}
   */
  @Deprecated
  @Nullable
  public static String makeGithubRepoUrlFromRemoteUrl(@NotNull String remoteUrl, @NotNull String host) {
    GHRepositoryPath repo = getUserAndRepositoryFromRemoteUrl(remoteUrl);
    if (repo == null) {
      return null;
    }
    return host + '/' + repo.getOwner() + '/' + repo.getRepository();
  }
  //endregion
}
