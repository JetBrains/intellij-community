// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.data.index;

import com.intellij.util.io.DataExternalizer;
import com.intellij.util.io.DataInputOutputUtil;
import org.jetbrains.annotations.NotNull;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

final class IntListDataExternalizer implements DataExternalizer<int[]> {
  @Override
  public void save(@NotNull DataOutput out, int[] value) throws IOException {
    DataInputOutputUtil.writeINT(out, value.length);
    for (int i : value) {
      out.writeInt(i);
    }
  }

  @Override
  public int[] read(@NotNull DataInput in) throws IOException {
    int size = DataInputOutputUtil.readINT(in);
    int[] value = new int[size];
    for (int i = 0; i < size; i++) {
      value[i] = in.readInt();
    }
    return value;
  }
}
