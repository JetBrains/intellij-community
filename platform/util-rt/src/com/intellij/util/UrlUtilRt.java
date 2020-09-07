// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util;

import com.intellij.openapi.diagnostic.LoggerRt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.MalformedURLException;
import java.net.URL;

public final class UrlUtilRt {
  /**
   * Interns a value of the {@link URL#protocol} ("file" or "jar") and {@link URL#host} (empty string) fields.
   *
   * @return interned URL or null if URL was malformed
   */
  @Nullable
  public static URL internProtocol(@NotNull URL url) {
    String protocol = url.getProtocol();
    boolean interned = false;
    if ("file".equals(protocol) || "jar".equals(protocol)) {
      protocol = protocol.intern();
      interned = true;
    }
    String host = url.getHost();
    if (host != null && host.isEmpty()) {
      host = "";
      interned = true;
    }
    try {
      if (interned) {
        url = new URL(protocol, host, url.getPort(), url.getFile());
      }
      return url;
    }
    catch (MalformedURLException e) {
      LoggerRt.getInstance(UrlUtilRt.class).error(e);
      return null;
    }
  }
}
