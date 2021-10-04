// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.lang.java6;

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

  @NotNull
  public abstract URL getURL();

  @NotNull
  public abstract InputStream getInputStream() throws IOException;

  @NotNull
  public abstract byte[] getBytes() throws IOException;

  public String getValue(@NotNull Attribute key) {
    return null;
  }

  @Nullable
  public ProtectionDomain getProtectionDomain() {
    return null;
  }

  @Override
  public String toString() {
    return getURL().toString();
  }
}
