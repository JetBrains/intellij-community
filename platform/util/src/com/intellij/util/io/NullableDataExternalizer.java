/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.util.io;

import org.jetbrains.annotations.Nullable;

import java.io.DataOutput;
import java.io.IOException;
import java.io.DataInput;

/**
 * @author peter
 */
public class NullableDataExternalizer<T> implements DataExternalizer<T> {
  private final DataExternalizer<T> myNotNullExternalizer;

  public NullableDataExternalizer(DataExternalizer<T> externalizer) {
    myNotNullExternalizer = externalizer;
  }

  public void save(DataOutput out, T value) throws IOException {
    if (value == null) {
      out.writeBoolean(false);
    } else {
      out.writeBoolean(true);
      myNotNullExternalizer.save(out, value);
    }
  }

  @Nullable
  public T read(DataInput in) throws IOException {
    final boolean isDefined = in.readBoolean();
    if (isDefined) {
      return myNotNullExternalizer.read(in);
    }
    return null;
  }
}
