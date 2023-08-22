// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.io;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

public final class VoidDataExternalizer implements DataExternalizer<Void> {
  public static final VoidDataExternalizer INSTANCE = new VoidDataExternalizer();

  @Override
  public void save(final @NotNull DataOutput out, final Void value) throws IOException {
  }

  @Override
  public @Nullable Void read(final @NotNull DataInput in) throws IOException {
    return null;
  }
}
