// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.mvstore.index;

import com.intellij.util.io.DataExternalizer;
import com.intellij.util.io.EnumeratorStringDescriptor;
import com.intellij.util.io.IntInlineKeyDescriptor;
import com.intellij.util.io.KeyDescriptor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.mvstore.type.DataType;
import org.jetbrains.mvstore.type.IntDataType;
import org.jetbrains.mvstore.type.KeyableDataType;
import org.jetbrains.mvstore.type.StringDataType;

class DataExternalizerDataTypeConverter {
  @SuppressWarnings("unchecked")
  @NotNull
  static <T> DataType<T> convert(@NotNull DataExternalizer<T> externalizer) {
    if (externalizer instanceof IntInlineKeyDescriptor) {
      return (DataType<T>)IntDataType.INSTANCE;
    }
    if (externalizer instanceof EnumeratorStringDescriptor) {
      return (DataType<T>)StringDataType.INSTANCE;
    }
    throw new IllegalArgumentException("unsupported externalizer");
  }

  @NotNull
  static <T> KeyableDataType<T> convert(@NotNull KeyDescriptor<T> descriptor) {
    DataType<T> dataType = convert((DataExternalizer<T>)descriptor);
    return (KeyableDataType<T>)dataType;
  }
}
