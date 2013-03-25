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
package org.jetbrains.plugins.javaFX;

import com.intellij.util.io.DataExternalizer;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

/**
* User: anna
* Date: 3/14/13
*/
public class FxmlDataExternalizer implements DataExternalizer<Set<String>> {
  @Override
  public void save(DataOutput out, Set<String> value) throws IOException {
    out.writeInt(value.size());
    for (String s : value) {
      out.writeUTF(s);
    }
  }

  @Override
  public Set<String> read(DataInput in) throws IOException {
    final int size = in.readInt();
    final Set<String> result = new HashSet<String>(size);

    for (int i = 0; i < size; i++) {
      final String s = in.readUTF();
      result.add(s);
    }
    return result;
  }
}
