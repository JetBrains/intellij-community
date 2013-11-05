/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

/**
 * @author Eugene Zhuravlev
*         Date: Dec 18, 2007
*/
public class EnumeratorStringDescriptor implements KeyDescriptor<String> {
  private final byte[] buffer = IOUtil.allocReadWriteUTFBuffer();

  @Override
  public int getHashCode(final String value) {
    return value.hashCode();
  }

  @Override
  public boolean isEqual(final String val1, final String val2) {
    return val1.equals(val2);
  }

  @Override
  public void save(final DataOutput storage, @NotNull final String value) throws IOException {
    IOUtil.writeUTFFast(buffer, storage, value);
  }

  @Override
  public String read(final DataInput storage) throws IOException {
    return IOUtil.readUTFFast(buffer, storage);
  }
}
