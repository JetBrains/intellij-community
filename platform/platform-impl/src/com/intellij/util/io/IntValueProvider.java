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
 * @author max
 */
public class IntValueProvider implements ByteBufferMap.ValueProvider<Integer> {
  public static IntValueProvider INSTANCE = new IntValueProvider();

  private IntValueProvider() {
  }

  public void write(DataOutput out, Integer value) throws IOException {
    out.writeInt(((Integer)value).intValue());
  }

  public int length(Integer value) {
    return 4;
  }

  public Integer get(DataInput in) throws IOException {
    return new Integer(in.readInt());
  }
}
