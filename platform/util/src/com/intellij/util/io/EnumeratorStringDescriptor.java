// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.io;

import org.jetbrains.annotations.NotNull;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

/**
 * Descriptor for serializing identifiers. Use {@link #INSTANCE}.
 *
 * @see CaseInsensitiveEnumeratorStringDescriptor
 */
public class EnumeratorStringDescriptor implements KeyDescriptor<String> {
  private static class EnumeratorStringDescriptorImpl
    extends EnumeratorStringDescriptor
    implements DifferentSerializableBytesImplyNonEqualityPolicy {
  }

  public static final EnumeratorStringDescriptor INSTANCE = new EnumeratorStringDescriptorImpl();

  @Override
  public int getHashCode(final String value) {
    return value.hashCode();
  }

  @Override
  public boolean isEqual(final String val1, final String val2) {
    return val1.equals(val2);
  }

  @Override
  public void save(@NotNull final DataOutput storage, @NotNull final String value) throws IOException {
    IOUtil.writeUTF(storage, value);
  }

  @Override
  public String read(@NotNull final DataInput storage) throws IOException {
    return IOUtil.readUTF(storage);
  }
}
