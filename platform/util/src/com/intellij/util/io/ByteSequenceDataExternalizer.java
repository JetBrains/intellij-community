// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.io;

import com.intellij.openapi.util.io.ByteArraySequence;
import org.jetbrains.annotations.NotNull;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.io.InputStream;

/**
 * @author Maxim.Mossienko
 */
public final class ByteSequenceDataExternalizer implements DataExternalizer<ByteArraySequence> {
  public static final ByteSequenceDataExternalizer INSTANCE = new ByteSequenceDataExternalizer();

  @Override
  public void save(@NotNull DataOutput out, ByteArraySequence value) throws IOException {
    out.write(value.getInternalBuffer(), value.getOffset(), value.getLength()); // todo fix double copying
  }

  @Override
  public ByteArraySequence read(@NotNull DataInput in) throws IOException {
    byte[] buf = new byte[((InputStream)in).available()]; // todo fix double copying
    in.readFully(buf);
    return ByteArraySequence.create(buf);
  }
}
