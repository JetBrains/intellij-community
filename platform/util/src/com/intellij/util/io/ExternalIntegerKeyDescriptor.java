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

public final class ExternalIntegerKeyDescriptor implements KeyDescriptor<Integer> {

  public static final ExternalIntegerKeyDescriptor INSTANCE = new ExternalIntegerKeyDescriptor();

  public ExternalIntegerKeyDescriptor() {
  }

  @Override
  public int getHashCode(Integer value) {
    return value;
  }

  @Override
  public boolean isEqual(Integer val1, Integer val2) {
    return val1.equals(val2);
  }

  @Override
  public void save(@NotNull DataOutput out, Integer value) throws IOException {
    DataInputOutputUtil.writeINT(out, value);
  }

  @Override
  public Integer read(@NotNull DataInput in) throws IOException {
    return DataInputOutputUtil.readINT(in);
  }
}