// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.lang;

import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.io.UnsyncByteArrayInputStream;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

final class MemoryResource extends Resource {
  private final URL url;
  private final byte[] myContent;
  private final Map<Resource.Attribute, String> myAttributes;

  private MemoryResource(@NotNull URL url, byte @NotNull [] content, @Nullable Map<Resource.Attribute, String> attributes) {
    this.url = url;
    myContent = content;
    myAttributes = attributes;
  }

  @Override
  public @NotNull URL getURL() {
    return url;
  }

  @Override
  public @NotNull InputStream getInputStream() {
    return new UnsyncByteArrayInputStream(myContent);
  }

  @Override
  public byte @NotNull [] getBytes() {
    return myContent;
  }

  @Override
  public String getValue(@NotNull Attribute key) {
    return myAttributes != null ? myAttributes.get(key) : null;
  }

  static @NotNull MemoryResource load(@NotNull URL baseUrl,
                                      @NotNull ZipFile zipFile,
                                      @NotNull ZipEntry entry,
                                      @Nullable Map<Attribute, String> attributes) throws IOException {
    byte[] content = ArrayUtilRt.EMPTY_BYTE_ARRAY;
    InputStream stream = zipFile.getInputStream(entry);
    if (stream != null) {
      try {
        content = FileUtilRt.loadBytes(stream, (int)entry.getSize());
      }
      finally {
        stream.close();
      }
    }

    return new MemoryResource(new URL(baseUrl, entry.getName()), content, attributes);
  }
}
