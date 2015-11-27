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

import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.github.api.GithubApiUtil;
import org.jetbrains.plugins.github.api.GithubFullPath;

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

  @NotNull
  public static String removeTrailingSlash(@NotNull String s) {
    if (s.endsWith("/")) {
      return s.substring(0, s.length() - 1);
    }
    return s;
  }

  @NotNull
  public static String getApiUrl(@NotNull String urlFromSettings) {
    return getApiProtocolFromUrl(urlFromSettings) + getApiUrlWithoutProtocol(urlFromSettings);
  }

  @NotNull
  public static String getApiProtocol() {
    return getApiProtocolFromUrl(GithubSettings.getInstance().getHost());
  }

  @NotNull
  public static String getApiProtocolFromUrl(@NotNull String urlFromSettings) {
    if (StringUtil.startsWithIgnoreCase(urlFromSettings.trim(), "http://")) return "http://";
    return "https://";
  }

  /**
   * E.g.: https://api.github.com
   *       https://my.company.url/api/v3
   */
  @NotNull
  public static String getApiUrl() {
    return getApiUrl(GithubSettings.getInstance().getHost());
  }

  /**
   * Returns the "host" part of Github URLs.
   * E.g.: https://github.com
   *       https://my.company.url
   * Note: there is no trailing slash in the returned url.
   */
  @NotNull
  public static String getGithubHost() {
    return getApiProtocol() + getGitHostWithoutProtocol();
  }

  /**
   * E.g.: https://github.com/suffix/ -> github.com
   *       github.com:8080/ -> github.com
   */
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
   * E.g.: github.com
   *       my.company.url
   */
  @NotNull
  public static String getGitHostWithoutProtocol() {
    return removeTrailingSlash(removeProtocolPrefix(GithubSettings.getInstance().getHost()));
  }

  /*
     All API access is over HTTPS, and accessed from the api.github.com domain
     (or through yourdomain.com/api/v3/ for enterprise).
     http://developer.github.com/api/v3/
    */
  @NotNull
  public static String getApiUrlWithoutProtocol(@NotNull String urlFromSettings) {
    String url = removeTrailingSlash(removeProtocolPrefix(urlFromSettings.toLowerCase()));
    final String API_PREFIX = "api.";
    final String ENTERPRISE_API_SUFFIX = "/api/v3";

    if (url.equals(GithubApiUtil.DEFAULT_GITHUB_HOST)) {
      return API_PREFIX + url;
    }
    else if (url.equals(API_PREFIX + GithubApiUtil.DEFAULT_GITHUB_HOST)) {
      return url;
    }
    else if (url.endsWith(ENTERPRISE_API_SUFFIX)) {
      return url;
    }
    else {
      return url + ENTERPRISE_API_SUFFIX;
    }
  }

  public static boolean isGithubUrl(@NotNull String url) {
    return isGithubUrl(url, GithubSettings.getInstance().getHost());
  }

  public static boolean isGithubUrl(@NotNull String url, @NotNull String host) {
    host = getHostFromUrl(host);
    url = removeProtocolPrefix(url);
    if (StringUtil.startsWithIgnoreCase(url, host)) {
      if (url.length() > host.length() && ":/".indexOf(url.charAt(host.length())) == -1) {
        return false;
      }
      return true;
    }
    return false;
  }

  /**
   * assumed isGithubUrl(remoteUrl)
   *
   * git@github.com:user/repo.git -> user/repo
   */
  @Nullable
  public static GithubFullPath getUserAndRepositoryFromRemoteUrl(@NotNull String remoteUrl) {
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
    return new GithubFullPath(username, reponame);
  }

  /**
   * assumed isGithubUrl(remoteUrl)
   *
   * git@github.com:user/repo -> https://github.com/user/repo
   */
  @Nullable
  public static String makeGithubRepoUrlFromRemoteUrl(@NotNull String remoteUrl) {
    return makeGithubRepoUrlFromRemoteUrl(remoteUrl, getGithubHost());
  }

  @Nullable
  public static String makeGithubRepoUrlFromRemoteUrl(@NotNull String remoteUrl, @NotNull String host) {
    GithubFullPath repo = getUserAndRepositoryFromRemoteUrl(remoteUrl);
    if (repo == null) {
      return null;
    }
    return host + '/' + repo.getUser() + '/' + repo.getRepository();
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

  @NotNull
  public static String getCloneUrl(@NotNull GithubFullPath path) {
    return getCloneUrl(path.getUser(), path.getRepository());
  }

  @NotNull
  public static String getCloneUrl(@NotNull String user, @NotNull String repo) {
    if (GithubSettings.getInstance().isCloneGitUsingSsh()) {
      return "git@" + getGitHostWithoutProtocol() + ":" + user + "/" + repo + ".git";
    }
    else {
      return getApiProtocol() + getGitHostWithoutProtocol() + "/" + user + "/" + repo + ".git";
    }
  }
}
