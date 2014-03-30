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

/*
 * @author max
 */
package com.intellij.util.io;

import org.jetbrains.annotations.NotNull;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

public abstract class InlineKeyDescriptor<T> implements KeyDescriptor<T> {
  private final boolean myCompactFormat = isCompactFormat();

  protected boolean isCompactFormat() {
    return false;
  }

  @Override
  public final int getHashCode(T value) {
    return toInt(value);
  }

  @Override
  public final boolean isEqual(T val1, T val2) {
    return toInt(val1) == toInt(val2);
  }

  @Override
  public final void save(@NotNull DataOutput out, T value) throws IOException {
    int v = toInt(value);
    if (myCompactFormat) DataInputOutputUtil.writeINT(out, v);
    else out.writeInt(v);
  }

  @Override
  public final T read(@NotNull DataInput in) throws IOException {
    int n;
    if (myCompactFormat) n = DataInputOutputUtil.readINT(in);
    else n = in.readInt();
    return fromInt(n);
  }

  public abstract T fromInt(int n);

  public abstract int toInt(T t);
}
