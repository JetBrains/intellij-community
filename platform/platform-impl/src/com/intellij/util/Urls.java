/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
import com.intellij.openapi.util.SystemInfoRt;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.StandardFileSystems;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.io.URLUtil;
import gnu.trove.TObjectHashingStrategy;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collection;
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
  public static Url newLocalFileUrl(@NotNull String path) {
    return new LocalFileUrl(FileUtilRt.toSystemIndependentName(path));
  }

  @NotNull
  public static Url newLocalFileUrl(@NotNull VirtualFile file) {
    return new LocalFileUrl(file.getPath());
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
    return newUrl("http", authority, path);
  }

  @NotNull
  public static Url newUrl(@NotNull String scheme, @NotNull String authority, @Nullable String path) {
    return new UrlImpl(scheme, authority, path);
  }

  @NotNull
  /**
   * Url will not be normalized (see {@link VfsUtilCore#toIdeaUrl(String)}), parsed as is
   */
  public static Url newFromIdea(@NotNull CharSequence url) {
    Url result = parseFromIdea(url);
    LOG.assertTrue(result != null, url);
    return result;
  }

  // java.net.URI.create cannot parse "file:///Test Stuff" - but you don't need to worry about it - this method is aware
  @Nullable
  public static Url parseFromIdea(@NotNull CharSequence url) {
    for (int i = 0, n = url.length(); i < n; i++) {
      char c = url.charAt(i);
      if (c == ':') {
        // file:// or dart:core/foo
        return parseUrl(url);
      }
      else if (c == '/' || c == '\\') {
        return newLocalFileUrl(url.toString());
      }
    }
    return newLocalFileUrl(url.toString());
  }

  @Nullable
  public static Url parse(@NotNull String url, boolean asLocalIfNoScheme) {
    if (url.isEmpty()) {
      return null;
    }

    if (asLocalIfNoScheme && !URLUtil.containsScheme(url)) {
      // nodejs debug - files only in local filesystem
      return newLocalFileUrl(url);
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
      LOG.info("Cannot parse url " + url, e);
      return null;
    }
  }

  @Nullable
  private static Url parseUrl(@NotNull CharSequence url) {
    CharSequence urlToParse;
    if (StringUtil.startsWith(url, "jar:file://")) {
      urlToParse = url.subSequence("jar:".length(), url.length());
    }
    else {
      urlToParse = url;
    }

    Matcher matcher = URI_PATTERN.matcher(urlToParse);
    if (!matcher.matches()) {
      return null;
    }
    String scheme = matcher.group(1);
    if (urlToParse != url) {
      scheme = "jar:" + scheme;
    }

    String authority = StringUtil.nullize(matcher.group(3));
    String path = StringUtil.nullize(matcher.group(4));
    boolean hasUrlSeparator = !StringUtil.isEmpty(matcher.group(2));
    if (authority == null) {
      if (hasUrlSeparator) {
        authority = "";
      }
    }
    else if (StandardFileSystems.FILE_PROTOCOL.equals(scheme) || !hasUrlSeparator) {
      path = path == null ? authority : (authority + path);
      authority = hasUrlSeparator ? "" : null;
    }

    // canonicalize only if authority is not empty or file url - we should not canonicalize URL with unknown scheme (webpack:///./modules/flux-orion-plugin/fluxPlugin.ts)
    if (path != null && (!StringUtil.isEmpty(authority) || StandardFileSystems.FILE_PROTOCOL.equals(scheme))) {
      path = FileUtil.toCanonicalUriPath(path);
    }
    return new UrlImpl(scheme, authority, path, matcher.group(5));
  }

  @NotNull
  public static Url newFromVirtualFile(@NotNull VirtualFile file) {
    if (file.isInLocalFileSystem()) {
      return newUri(file.getFileSystem().getProtocol(), file.getPath());
    }
    else {
      Url url = parseUrl(file.getUrl());
      return url == null ? new UrlImpl(file.getPath()) : url;
    }
  }

  public static boolean equalsIgnoreParameters(@NotNull Url url, @NotNull Collection<Url> urls) {
    return equalsIgnoreParameters(url, urls, true);
  }

  public static boolean equalsIgnoreParameters(@NotNull Url url, @NotNull Collection<Url> urls, boolean caseSensitive) {
    for (Url otherUrl : urls) {
      if (equals(url, otherUrl, caseSensitive, true)) {
        return true;
      }
    }
    return false;
  }

  public static boolean equalsIgnoreParameters(@NotNull Url url, @NotNull VirtualFile file) {
    if (file.isInLocalFileSystem()) {
      return url.isInLocalFileSystem() && (SystemInfoRt.isFileSystemCaseSensitive
                                           ? url.getPath().equals(file.getPath()) :
                                           url.getPath().equalsIgnoreCase(file.getPath()));
    }
    else if (url.isInLocalFileSystem()) {
      return false;
    }

    Url fileUrl = parseUrl(file.getUrl());
    return fileUrl != null && fileUrl.equalsIgnoreParameters(url);
  }

  public static boolean equals(@Nullable Url url1, @Nullable Url url2, boolean caseSensitive, boolean ignoreParameters) {
    if (url1 == null || url2 == null){
      return url1 == url2;
    }

    Url o1 = ignoreParameters ? url1.trimParameters() : url1;
    Url o2 = ignoreParameters ? url2.trimParameters() : url2;
    return caseSensitive ? o1.equals(o2) : o1.equalsIgnoreCase(o2);
  }

  @NotNull
  public static URI toUriWithoutParameters(@NotNull Url url) {
    try {
      String externalPath = url.getPath();
      boolean inLocalFileSystem = url.isInLocalFileSystem();
      if (inLocalFileSystem && SystemInfoRt.isWindows && externalPath.charAt(0) != '/') {
        externalPath = '/' + externalPath;
      }
      return new URI(inLocalFileSystem ? "file" : url.getScheme(), inLocalFileSystem ? "" : url.getAuthority(), externalPath, null, null);
    }
    catch (URISyntaxException e) {
      throw new RuntimeException(e);
    }
  }

  public static TObjectHashingStrategy<Url> getCaseInsensitiveUrlHashingStrategy() {
    return CaseInsensitiveUrlHashingStrategy.INSTANCE;
  }

  private static final class CaseInsensitiveUrlHashingStrategy implements TObjectHashingStrategy<Url> {
    private static final TObjectHashingStrategy<Url> INSTANCE = new CaseInsensitiveUrlHashingStrategy();

    @Override
    public int computeHashCode(Url url) {
      return url == null ? 0 : url.hashCodeCaseInsensitive();
    }

    @Override
    public boolean equals(Url url1, Url url2) {
      return Urls.equals(url1, url2, false, false);
    }
  }
}