// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.io;

import com.intellij.openapi.util.io.ByteArraySequence;
import org.jetbrains.annotations.ApiStatus;

import java.io.DataOutput;
import java.io.IOException;

/**
 * A version of {@link DataExternalizer} that is able to directly convert input to {@link ByteArraySequence}.
 * Any {@link DataExternalizer} could be trivially made to produce {@link ByteArraySequence} by writing into
 * a byte[]-backed {@link java.io.DataInputStream}, and wrap the result into {@link ByteArraySequence} -- but the
 * idea is to use this interface for types T that allow for cheaper conversion, without intermediate
 * {@link java.io.DataInputStream}.
 * <p/>
 * Important: a binary format of {@link ByteArraySequence} produced by {@link #save(Object)} method MUST be compatible
 * with binary format produced by {@link DataExternalizer#save(DataOutput, Object)}.
 */
@ApiStatus.Internal
public interface ToByteArraySequenceExternalizer<T> extends DataExternalizer<T> {
  ByteArraySequence save(T value) throws IOException;
}
