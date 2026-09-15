// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.util.io.storages;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

/// Reports that persisted data uses an unsupported format version.
@ApiStatus.Internal
public final class UnsupportedFormatException extends IOException {
  private final @NotNull String subject;
  private final @NotNull String expectedVersion;
  private final @NotNull String actualVersion;

  public UnsupportedFormatException(@NotNull String subject, int expectedVersion, int actualVersion) {
    this(subject, Integer.toString(expectedVersion), Integer.toString(actualVersion));
  }

  public UnsupportedFormatException(@NotNull String subject,
                                    @NotNull String expectedVersion,
                                    @NotNull String actualVersion) {
    super("Unsupported format for " + subject + ": version " + actualVersion + ", expected " + expectedVersion);
    this.subject = subject;
    this.expectedVersion = expectedVersion;
    this.actualVersion = actualVersion;
  }

  public @NotNull String subject() {
    return subject;
  }

  public @NotNull String expectedVersion() {
    return expectedVersion;
  }

  public @NotNull String actualVersion() {
    return actualVersion;
  }
}
