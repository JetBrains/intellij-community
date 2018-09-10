// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.io;

import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.openapi.util.io.DataInputOutputUtilRt;
import com.intellij.util.ThrowableConsumer;
import org.jetbrains.annotations.NotNull;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.Collection;

public class IntCollectionDataExternalizer implements DataExternalizer<Collection<Integer>> {
  @Override
  public void save(@NotNull final DataOutput out, Collection<Integer> value) throws IOException {
    DataInputOutputUtilRt.writeSeq(out, value, new ThrowableConsumer<Integer, IOException>() {
      @Override
      public void consume(Integer i) throws IOException {
        EnumeratorIntegerDescriptor.INSTANCE.save(out, i);
      }
    });
  }

  @Override
  public Collection<Integer> read(@NotNull final DataInput in) throws IOException {
    return DataInputOutputUtilRt.readSeq(in, new ThrowableComputable<Integer, IOException>() {
      @Override
      public Integer compute() throws IOException {
        return EnumeratorIntegerDescriptor.INSTANCE.read(in);
      }
    });
  }
}
