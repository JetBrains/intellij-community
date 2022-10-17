/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.util.io;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

public class NullableDataExternalizer<T> implements DataExternalizer<T> {
  @NotNull
  private final DataExternalizer<T> myNotNullExternalizer;

  public NullableDataExternalizer(@NotNull DataExternalizer<T> externalizer) {
    myNotNullExternalizer = externalizer;
  }

  @Override
  public void save(@NotNull DataOutput out, T value) throws IOException {
    if (value == null) {
      out.writeBoolean(false);
    } else {
      out.writeBoolean(true);
      myNotNullExternalizer.save(out, value);
    }
  }

  @Override
  @Nullable
  public T read(@NotNull DataInput in) throws IOException {
    final boolean isDefined = in.readBoolean();
    if (isDefined) {
      return myNotNullExternalizer.read(in);
    }
    return null;
  }
}
