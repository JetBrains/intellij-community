// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;

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
      //noinspection CallToPrintStackTrace
      e.printStackTrace();
      return null;
    }
  }

  @NotNull
  public static  CharSequence unescapePercentSequences(@NotNull CharSequence s, int from, int end) {
    int i = indexOf(s, from, end);
    if (i == -1) {
      return s.subSequence(from, end);
    }

    StringBuilder decoded = new StringBuilder();
    decoded.append(s, from, i);

    byte[] byteBuffer = null;
    int byteBufferSize = 0;
    while (i < end) {
      char c = s.charAt(i);
      if (c == '%') {
        if (byteBuffer == null) {
          byteBuffer = new byte[end - from];
        }
        else {
          byteBufferSize = 0;
        }
        while (i + 2 < end && s.charAt(i) == '%') {
          final int d1 = decode(s.charAt(i + 1));
          final int d2 = decode(s.charAt(i + 2));
          if (d1 != -1 && d2 != -1) {
            byteBuffer[byteBufferSize++] = (byte)((d1 & 0xf) << 4 | d2 & 0xf);
            i += 3;
          }
          else {
            break;
          }
        }
        if (byteBufferSize != 0) {
          decoded.append(new String(byteBuffer, 0, byteBufferSize, StandardCharsets.UTF_8));
          continue;
        }
      }

      decoded.append(c);
      i++;
    }
    return decoded;
  }

  private static int decode(char c) {
    if ((c >= '0') && (c <= '9')) {
      return c - '0';
    }
    if ((c >= 'a') && (c <= 'f')) {
      return c - 'a' + 10;
    }
    if ((c >= 'A') && (c <= 'F')) {
      return c - 'A' + 10;
    }
    return -1;
  }

  private static int indexOf(@NotNull CharSequence s, int start, int end) {
    end = Math.min(end, s.length());
    for (int i = Math.max(start, 0); i < end; i++) {
      if (s.charAt(i) == '%') {
        return i;
      }
    }
    return -1;
  }
}
