// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.lang;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;

public interface Resource {
  @NotNull URL getURL();

  @NotNull InputStream getInputStream() throws IOException;

  byte @NotNull [] getBytes() throws IOException;
}
