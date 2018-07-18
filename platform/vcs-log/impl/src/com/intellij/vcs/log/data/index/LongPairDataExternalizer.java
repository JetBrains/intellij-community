// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.data.index;

import com.intellij.openapi.util.Pair;
import com.intellij.util.io.DataExternalizer;
import org.jetbrains.annotations.NotNull;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

public class LongPairDataExternalizer implements DataExternalizer<Pair<Long, Long>> {
  @Override
  public void save(@NotNull DataOutput out, Pair<Long, Long> value) throws IOException {
    out.writeLong(value.first);
    out.writeLong(value.second);
  }

  @Override
  public Pair<Long, Long> read(@NotNull DataInput in) throws IOException {
    return Pair.create(in.readLong(), in.readLong());
  }
}
