// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.data.index;

import com.intellij.util.SmartList;
import com.intellij.util.io.DataExternalizer;
import com.intellij.util.io.DataInputOutputUtil;
import org.jetbrains.annotations.NotNull;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.List;

class IntListDataExternalizer implements DataExternalizer<List<Integer>> {
  @Override
  public void save(@NotNull DataOutput out, List<Integer> value) throws IOException {
    DataInputOutputUtil.writeINT(out, value.size());
    for (Integer i : value) {
      out.writeInt(i);
    }
  }

  @Override
  public List<Integer> read(@NotNull DataInput in) throws IOException {
    List<Integer> value = new SmartList<>();

    int size = DataInputOutputUtil.readINT(in);
    for (int i = 0; i < size; i++) {
      value.add(in.readInt());
    }

    return value;
  }
}
