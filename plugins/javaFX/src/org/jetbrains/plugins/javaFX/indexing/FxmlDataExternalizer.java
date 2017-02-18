/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package org.jetbrains.plugins.javaFX.indexing;

import com.intellij.util.io.DataExternalizer;
import com.intellij.util.io.DataInputOutputUtil;
import com.intellij.util.io.IOUtil;
import org.jetbrains.annotations.NotNull;

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
  public void save(@NotNull DataOutput out, Set<String> value) throws IOException {
    DataInputOutputUtil.writeINT(out, value.size());
    for (String s : value) {
      IOUtil.writeUTF(out, s);
    }
  }

  @Override
  public Set<String> read(@NotNull DataInput in) throws IOException {
    final int size = DataInputOutputUtil.readINT(in);
    final Set<String> result = new HashSet<>(size);

    for (int i = 0; i < size; i++) {
      final String s = IOUtil.readUTF(in);
      result.add(s);
    }
    return result;
  }
}
