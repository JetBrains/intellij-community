// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.io;

import com.intellij.openapi.util.io.DataInputOutputUtilRt;
import org.jetbrains.annotations.NotNull;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.Collection;

public final class IntCollectionDataExternalizer implements DataExternalizer<Collection<Integer>> {
  @Override
  public void save(@NotNull final DataOutput out, Collection<Integer> value) throws IOException {
    DataInputOutputUtilRt.writeSeq(out, value, i -> EnumeratorIntegerDescriptor.INSTANCE.save(out, i));
  }

  @Override
  public Collection<Integer> read(@NotNull final DataInput in) throws IOException {
    return DataInputOutputUtilRt.readSeq(in, () -> EnumeratorIntegerDescriptor.INSTANCE.read(in));
  }
}
