/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

/**
 * @author peter
 */
public class EnumDataDescriptor<T extends Enum> implements KeyDescriptor<T> {
  private final Class<T> myEnumClass;

  public EnumDataDescriptor(Class<T> enumClass) {
    myEnumClass = enumClass;
  }

  public int getHashCode(final T value) {
    return value.hashCode();
  }

  public boolean isEqual(final T val1, final T val2) {
    return val1.equals(val2);
  }

  public void save(final DataOutput out, final T value) throws IOException {
    out.writeInt(value.ordinal());
  }

  public T read(final DataInput in) throws IOException {
    return myEnumClass.getEnumConstants()[in.readInt()];
  }
}