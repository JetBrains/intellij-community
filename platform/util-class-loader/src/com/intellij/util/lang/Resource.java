// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.lang;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.ByteBuffer;

public interface Resource {
  @NotNull URL getURL();

  @NotNull InputStream getInputStream() throws IOException;

  byte @NotNull [] getBytes() throws IOException;

  default @NotNull ByteBuffer getByteBuffer() throws IOException {
    return ByteBuffer.wrap(getBytes());
  }
}
