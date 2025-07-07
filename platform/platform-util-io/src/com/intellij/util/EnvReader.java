// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util;

import org.jetbrains.annotations.ApiStatus;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/** Obsolete internal class; use {@link ShellEnvironmentReader} instead. */
@ApiStatus.Internal
@Deprecated(forRemoval = true)
@SuppressWarnings("ALL")
public class EnvReader {
  public EnvReader() {
    throw new UnsupportedOperationException();
  }

  public EnvReader(long timeoutMillis) {
    throw new UnsupportedOperationException();
  }

  protected String getShell() {
    throw new UnsupportedOperationException();
  }

  protected List getShellProcessCommand() {
    throw new UnsupportedOperationException();
  }

  public final Map<String, String> readShellEnv(Path file, Map<String, String> parentEnv) throws IOException {
    throw new UnsupportedOperationException();
  }

  public final Map<String, String> readBatEnv(Path file, List<String> args) throws IOException {
    throw new UnsupportedOperationException();
  }
}
