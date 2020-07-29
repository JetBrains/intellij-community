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
  private final URL myUrl;
  private final byte[] myContent;
  private final Map<Resource.Attribute, String> myAttributes;

  private MemoryResource(@NotNull URL url, @NotNull byte[] content, @Nullable Map<Resource.Attribute, String> attributes) {
    myUrl = url;
    myContent = content;
    myAttributes = attributes;
  }

  @NotNull
  @Override
  public URL getURL() {
    return myUrl;
  }

  @NotNull
  @Override
  public InputStream getInputStream() {
    return new UnsyncByteArrayInputStream(myContent);
  }

  @NotNull
  @Override
  public byte[] getBytes() {
    return myContent;
  }

  @Override
  public String getValue(@NotNull Attribute key) {
    return myAttributes != null ? myAttributes.get(key) : null;
  }

  @NotNull
  static MemoryResource load(@NotNull URL baseUrl,
                             @NotNull ZipFile zipFile,
                             @NotNull ZipEntry entry,
                             @Nullable Map<Attribute, String> attributes) throws IOException {
    String name = entry.getName();
    URL url = new URL(baseUrl, name);

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

    return new MemoryResource(url, content, attributes);
  }
}
