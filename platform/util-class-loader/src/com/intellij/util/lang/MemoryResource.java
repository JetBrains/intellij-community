// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.lang;

import com.intellij.util.io.UnsyncByteArrayInputStream;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Map;

public final class MemoryResource extends Resource {
  private final URL url;
  private final byte[] content;
  private final Map<Resource.Attribute, String> attributes;

  public MemoryResource(@NotNull String baseUrl,
                         byte[] content,
                         @NotNull String name,
                         @Nullable Map<Attribute, String> attributes) throws MalformedURLException {
    this.url = new URL("jar", "", -1, baseUrl + "!/" + name);
    this.content = content;
    this.attributes = attributes;
  }

  @Override
  public @NotNull URL getURL() {
    return url;
  }

  @Override
  public @NotNull InputStream getInputStream() {
    return new UnsyncByteArrayInputStream(content);
  }

  @Override
  public byte @NotNull [] getBytes() {
    return content;
  }

  @Override
  public @Nullable Map<Attribute, String> getAttributes() {
    return attributes;
  }
}
