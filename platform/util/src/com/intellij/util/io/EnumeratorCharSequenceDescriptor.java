// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.io;

import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

public final class EnumeratorCharSequenceDescriptor implements KeyDescriptor<CharSequence>, DifferentSerializableBytesImplyNonEqualityPolicy {
  public static final EnumeratorCharSequenceDescriptor INSTANCE = new EnumeratorCharSequenceDescriptor();

  @Override
  public int getHashCode(final CharSequence value) {
    return value.hashCode();
  }

  @Override
  public boolean isEqual(final CharSequence val1, final CharSequence val2) {
    return StringUtil.equals(val1, val2);
  }

  @Override
  public void save(final @NotNull DataOutput storage, final @NotNull CharSequence value) throws IOException {
    IOUtil.writeUTF(storage, value);
  }

  @Override
  public CharSequence read(final @NotNull DataInput storage) throws IOException {
    return IOUtil.readUTFCharSequence(storage);
  }
}

