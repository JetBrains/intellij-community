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
package com.intellij.util;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.StandardFileSystems;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.io.URLUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class Urls {
  private static final Logger LOG = Logger.getInstance(Urls.class);

  // about ";" see WEB-100359
  private static final Pattern URI_PATTERN = Pattern.compile("^([^:/?#]+):(//)?([^/?#]*)([^?#;]*)(.*)");

  @NotNull
  public static Url newUri(@NotNull String scheme, @NotNull String path) {
    return new UrlImpl(scheme, null, path);
  }

  @NotNull
  public static Url newFromEncoded(@NotNull String url) {
    Url result = parseEncoded(url);
    LOG.assertTrue(result != null, url);
    return result;
  }

  @Nullable
  public static Url parseEncoded(@NotNull String url) {
    return parse(url, false);
  }

  @NotNull
  public static Url newHttpUrl(@NotNull String authority, @Nullable String path) {
    return new UrlImpl("http", authority, path);
  }

  @NotNull
  public static Url newFromIdea(@NotNull String url) {
    Url result = parseFromIdea(url);
    LOG.assertTrue(result != null, url);
    return result;
  }

  // java.net.URI.create cannot parse "file:///Test Stuff" - but you don't need to worry about it - this method is aware
  @Nullable
  public static Url parseFromIdea(@NotNull String url) {
    return URLUtil.containsScheme(url) ? parseUrl(url) : new LocalFileUrl(url);
  }

  @Nullable
  public static Url parse(@NotNull String url, boolean asLocalIfNoScheme) {
    if (asLocalIfNoScheme && !URLUtil.containsScheme(url)) {
      // nodejs debug — files only in local filesystem
      return new LocalFileUrl(url);
    }
    return parseUrl(VfsUtilCore.toIdeaUrl(url));
  }

  @Nullable
  public static URI parseAsJavaUriWithoutParameters(@NotNull String url) {
    Url asUrl = parseUrl(url);
    if (asUrl == null) {
      return null;
    }

    try {
      return toUriWithoutParameters(asUrl);
    }
    catch (Exception e) {
      LOG.info("Can't parse " + url, e);
      return null;
    }
  }

  @Nullable
  private static Url parseUrl(@NotNull String url) {
    String urlToParse;
    if (url.startsWith("jar:file://")) {
      urlToParse = url.substring("jar:".length());
    }
    else {
      urlToParse = url;
    }

    Matcher matcher = URI_PATTERN.matcher(urlToParse);
    if (!matcher.matches()) {
      LOG.warn("Cannot parse url " + url);
      return null;
    }
    String scheme = matcher.group(1);
    if (urlToParse != url) {
      scheme = "jar:" + scheme;
    }

    String authority = StringUtil.nullize(matcher.group(3));

    String path = StringUtil.nullize(matcher.group(4));
    if (path != null) {
      path = FileUtil.toCanonicalUriPath(path);
    }

    if (authority != null && (StandardFileSystems.FILE_PROTOCOL.equals(scheme) || StringUtil.isEmpty(matcher.group(2)))) {
      path = path == null ? authority : (authority + path);
      authority = null;
    }
    return new UrlImpl(scheme, authority, path, matcher.group(5));
  }

  // must not be used in NodeJS
  public static Url newFromVirtualFile(@NotNull VirtualFile file) {
    if (file.isInLocalFileSystem()) {
      return newUri(file.getFileSystem().getProtocol(), file.getPath());
    }
    else {
      return parseUrl(file.getUrl());
    }
  }

  public static boolean equalsIgnoreParameters(@NotNull Url url, @NotNull VirtualFile file) {
    if (file.isInLocalFileSystem()) {
      return url.isInLocalFileSystem() && url.getPath().equals(file.getPath());
    }
    else if (url.isInLocalFileSystem()) {
      return false;
    }

    Url fileUrl = parseUrl(file.getUrl());
    return fileUrl != null && fileUrl.equalsIgnoreParameters(url);
  }

  @NotNull
  public static URI toUriWithoutParameters(@NotNull Url url) {
    try {
      String externalPath = url.getPath();
      boolean inLocalFileSystem = url.isInLocalFileSystem();
      if (inLocalFileSystem && SystemInfo.isWindows && externalPath.charAt(0) != '/') {
        externalPath = '/' + externalPath;
      }
      return new URI(inLocalFileSystem ? "file" : url.getScheme(), inLocalFileSystem ? "" : url.getAuthority(), externalPath, null, null);
    }
    catch (URISyntaxException e) {
      throw new RuntimeException(e);
    }
  }
}