// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.io;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

public final class NullableDataExternalizer<T> implements DataExternalizer<T> {
  private final @NotNull DataExternalizer<T> myNotNullExternalizer;

  public NullableDataExternalizer(@NotNull DataExternalizer<T> externalizer) {
    myNotNullExternalizer = externalizer;
  }

  @Override
  public void save(@NotNull DataOutput out, T value) throws IOException {
    if (value == null) {
      out.writeBoolean(false);
    } else {
      out.writeBoolean(true);
      myNotNullExternalizer.save(out, value);
    }
  }

  @Override
  public @Nullable T read(@NotNull DataInput in) throws IOException {
    final boolean isDefined = in.readBoolean();
    if (isDefined) {
      return myNotNullExternalizer.read(in);
    }
    return null;
  }
}
