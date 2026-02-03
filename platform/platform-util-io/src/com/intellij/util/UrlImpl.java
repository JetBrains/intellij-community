// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.io.URLUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

final class UrlImpl implements Url {
  private final String scheme;
  private final String authority;

  private final String path;
  private String decodedPath;

  private final String parameters;

  private String externalForm;
  private UrlImpl withoutParameters;

  UrlImpl(@Nullable String scheme, @Nullable String authority, @Nullable String path) {
    this(scheme, authority, path, null);
  }

  UrlImpl(@Nullable String scheme, @Nullable String authority, @Nullable String path, @Nullable String parameters) {
    this.scheme = scheme;
    this.authority = authority;
    this.path = StringUtil.notNullize(path);
    this.parameters = StringUtil.nullize(parameters);
  }

  @Override
  public @NotNull Url resolve(@NotNull String subPath) {
    String newPath = path.isEmpty() && authority == null ? subPath : StringUtil.trimEnd(path, '/') + "/" + subPath;
    return new UrlImpl(scheme, authority, newPath, parameters);
  }

  @Override
  public @NotNull Url addParameters(@NotNull Map<String, String> parameters) {
    if (parameters.isEmpty()) {
      return this;
    }

    StringBuilder builder = new StringBuilder();
    if (this.parameters == null) {
      builder.append('?');
    }
    else {
      builder.append(this.parameters);
      builder.append('&');
    }
    Urls.encodeParameters(parameters, builder);
    return new UrlImpl(scheme, authority, path, builder.toString());
  }

  @Override
  public @NotNull String getPath() {
    if (decodedPath == null) {
      decodedPath = URLUtil.unescapePercentSequences(path);
    }
    return decodedPath;
  }

  @Override
  public @Nullable String getScheme() {
    return scheme;
  }

  @Override
  public @Nullable String getAuthority() {
    return authority;
  }

  @Override
  public boolean isInLocalFileSystem() {
    return URLUtil.FILE_PROTOCOL.equals(scheme);
  }

  @Override
  public @Nullable String getParameters() {
    return parameters;
  }

  @Override
  public String toDecodedForm() {
    StringBuilder builder = new StringBuilder();
    if (scheme != null) {
      builder.append(scheme);
      if (authority == null) {
        builder.append(':');
      }
      else {
        builder.append(URLUtil.SCHEME_SEPARATOR);
      }

      if (authority != null) {
        builder.append(authority);
      }
    }
    builder.append(getPath());
    if (parameters != null) {
      builder.append(parameters);
    }
    return builder.toString();
  }

  @Override
  public @NotNull String toExternalForm() {
    if (externalForm != null) {
      return externalForm;
    }

    // relative path - special url, encoding is not required
    // authority is null in case of URI
    if ((authority == null || (!path.isEmpty() && path.charAt(0) != '/')) && !isInLocalFileSystem()) {
      return toDecodedForm();
    }

    String result = (StringUtil.isEmpty(authority) && StringUtil.isEmpty(path)) ?
                    scheme + "://" :
                    Urls.toUriWithoutParameters(this).toASCIIString();
    if (parameters != null) {
      result += parameters;
    }
    externalForm = result;
    return result;
  }

  @Override
  public @NotNull Url trimParameters() {
    if (parameters == null) {
      return this;
    }
    else if (withoutParameters == null) {
      withoutParameters = new UrlImpl(scheme, authority, path, null);
    }
    return withoutParameters;
  }

  @Override
  public String toString() {
    return toExternalForm();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof UrlImpl url)) return false;

    return StringUtil.equals(scheme, url.scheme) && StringUtil.equals(authority, url.authority) && getPath().equals(url.getPath()) && StringUtil.equals(parameters, url.parameters);
  }

  @Override
  public boolean equalsIgnoreCase(@Nullable Url o) {
    if (this == o) return true;
    if (!(o instanceof UrlImpl url)) return false;

    return StringUtil.equalsIgnoreCase(scheme, url.scheme) &&
           StringUtil.equalsIgnoreCase(authority, url.authority) &&
           getPath().equalsIgnoreCase(url.getPath()) &&
           StringUtil.equalsIgnoreCase(parameters, url.parameters);
  }

  @Override
  public boolean equalsIgnoreParameters(@Nullable Url url) {
    return url != null && trimParameters().equals(url.trimParameters());
  }

  private int computeHashCode(boolean caseSensitive) {
    int result = stringHashCode(scheme, caseSensitive);
    result = 31 * result + stringHashCode(authority, caseSensitive);
    result = 31 * result + stringHashCode(getPath(), caseSensitive);
    result = 31 * result + stringHashCode(parameters, caseSensitive);
    return result;
  }

  private static int stringHashCode(@Nullable CharSequence string, boolean caseSensitive) {
    return string == null ? 0 : (caseSensitive ? string.hashCode() : StringUtil.stringHashCodeInsensitive(string));
  }

  @Override
  public int hashCode() {
    return computeHashCode(true);
  }

  @Override
  public int hashCodeCaseInsensitive() {
    return computeHashCode(false);
  }

  @Override
  public @NotNull Url removeParameter(@NotNull String name) {
    StringBuilder result = new StringBuilder();
    String parameters = this.parameters;
    if (parameters == null) return this;
    
    if (parameters.startsWith("?")) {
      parameters = StringUtil.trimStart(parameters, "?");
      result.append("?");
    }
    boolean added = false;
    for (String s : parameters.split("&")) {
      String currentName = ContainerUtil.getFirstItem(StringUtil.split(s, "="));
      if (!StringUtil.equals(currentName, name)) {
        if (added) result.append("&");
        result.append(s);
        added = true;
      }
    }
    return new UrlImpl(scheme, authority, path, result.toString());
  }
}
