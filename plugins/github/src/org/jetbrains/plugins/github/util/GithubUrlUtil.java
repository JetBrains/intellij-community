// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.util;

import com.intellij.openapi.util.NlsSafe;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.github.api.GHRepositoryPath;

import static com.intellij.collaboration.hosting.GitHostingUrlUtil.removeProtocolPrefix;

/**
 * @author Aleksey Pivovarov
 */
public final class GithubUrlUtil {
  private static @NlsSafe @NotNull String removeTrailingSlash(@NotNull String s) {
    if (s.endsWith("/")) {
      return s.substring(0, s.length() - 1);
    }
    return s;
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

  private static @NlsSafe @NotNull String removeEndingDotGit(@NotNull String url) {
    url = removeTrailingSlash(url);
    final String DOT_GIT = ".git";
    if (url.endsWith(DOT_GIT)) {
      return url.substring(0, url.length() - DOT_GIT.length());
    }
    return url;
  }

  //region Deprecated

  /**
   * E.g.: https://github.com/suffix/ -> github.com
   * github.com:8080/ -> github.com
   *
   * @deprecated {@link org.jetbrains.plugins.github.api.GHRepositoryCoordinates}
   */
  @Deprecated(forRemoval = true)
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
  @Deprecated(forRemoval = true)
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
