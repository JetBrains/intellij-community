// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.lang;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.security.ProtectionDomain;

public abstract class Resource {
  public enum Attribute {
    SPEC_TITLE, SPEC_VERSION, SPEC_VENDOR, IMPL_TITLE, IMPL_VERSION, IMPL_VENDOR
  }

  public abstract @NotNull URL getURL();

  public abstract @NotNull InputStream getInputStream() throws IOException;

  public abstract byte @NotNull [] getBytes() throws IOException;

  public String getValue(@NotNull Attribute key) {
    return null;
  }

  public @Nullable ProtectionDomain getProtectionDomain() {
    return null;
  }

  @Override
  public String toString() {
    return getURL().toString();
  }

  @SuppressWarnings("DuplicatedCode")
  public static byte @NotNull [] loadBytes(@NotNull InputStream stream, int length) throws IOException {
    byte[] bytes = new byte[length];
    int count = 0;
    while (count < length) {
      int n = stream.read(bytes, count, length - count);
      if (n <= 0) break;
      count += n;
    }
    return bytes;
  }
}
