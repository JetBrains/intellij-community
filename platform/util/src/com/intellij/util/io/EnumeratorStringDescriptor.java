// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.io;

import org.jetbrains.annotations.NotNull;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

/**
 * Descriptor for serializing identifiers. Use {@link #INSTANCE}.
 * Does NOT support null values!
 *
 * @see CaseInsensitiveEnumeratorStringDescriptor
 */
public class EnumeratorStringDescriptor implements KeyDescriptor<String> {
  private static final class EnumeratorStringDescriptorImpl
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
  public void save(final @NotNull DataOutput storage, final @NotNull String value) throws IOException {
    IOUtil.writeUTF(storage, value);
  }

  @Override
  public String read(final @NotNull DataInput storage) throws IOException {
    return IOUtil.readUTF(storage);
  }
}
