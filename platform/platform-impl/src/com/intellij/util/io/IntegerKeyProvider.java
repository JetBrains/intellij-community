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

import com.intellij.openapi.diagnostic.Logger;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

/**
   * @deprecated use {@link ByteBufferIntObjectMap} instead
 */
public class IntegerKeyProvider implements ByteBufferMap.KeyProvider<Integer> {
  private static final Logger LOG = Logger.getInstance("#com.intellij.util.io.StringKeyProvider");

  public static final IntegerKeyProvider INSTANCE = new IntegerKeyProvider();

  private IntegerKeyProvider() {
  }

  public int hashCode(Integer key) {
    return key.hashCode();
  }

  public void write(DataOutput out, Integer key) throws IOException {
    out.writeInt(key.intValue());
  }

  public int length(Integer key) {
    return 4;
  }

  public Integer get(DataInput in) throws IOException {
    return new Integer(in.readInt());
  }

  public boolean equals(DataInput in, Integer key) throws IOException {
    return key.intValue() == in.readInt();
  }
}
