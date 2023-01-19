// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.data.index;

import com.intellij.util.io.DataExternalizer;
import org.jetbrains.annotations.NotNull;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

public final class LongPairDataExternalizer implements DataExternalizer<long[]> {
  @Override
  public void save(@NotNull DataOutput out, long[] value) throws IOException {
    out.writeLong(value[0]);
    out.writeLong(value[1]);
  }

  @Override
  public long[] read(@NotNull DataInput in) throws IOException {
    return new long[]{in.readLong(), in.readLong()};
  }
}
