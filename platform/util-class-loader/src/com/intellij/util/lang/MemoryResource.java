// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.lang;

import com.intellij.util.io.UnsyncByteArrayInputStream;
import org.jetbrains.annotations.NotNull;

import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;

public final class MemoryResource implements Resource {
  private URL url;
  private final byte[] content;
  private final String name;
  private final String baseUrl;

  public MemoryResource(@NotNull String baseUrl, byte[] content, @NotNull String name) {
    this.baseUrl = baseUrl;
    this.content = content;
    this.name = name;
  }

  @Override
  public String toString() {
    return baseUrl + "/" + name;
  }

  @Override
  public @NotNull URL getURL() {
    URL result = url;
    if (result == null) {
      try {
        result = new URL("jar", "", -1, baseUrl + "!/" + name);
      }
      catch (MalformedURLException e) {
        throw new RuntimeException(e);
      }
      url = result;
    }
    return result;
  }

  @Override
  public @NotNull InputStream getInputStream() {
    return new UnsyncByteArrayInputStream(content);
  }

  @Override
  public byte @NotNull [] getBytes() {
    return content;
  }
}
